package searchengine.services;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import searchengine.config.ConnectionProperties;
import searchengine.config.SitesList;
import searchengine.dto.statistics.RequestResponceFailed;
import searchengine.dto.statistics.RequestResponceSucceeded;
import searchengine.dto.statistics.RequestResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.ModelPage;
import searchengine.model.ModelSite;
import searchengine.model.Status;
import searchengine.processors.WordProcessor;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

@RequiredArgsConstructor
@Service
public class PageIndexingService {
  @Autowired
  public PagesRepository pagesRepository;
  @Autowired
  public SitesRepository sitesRepository;
  @Autowired
  public ConnectionProperties connectionProperties;
  @Autowired
  public SitesList initialConSites;
  @Autowired
  public LemmaRepository lemmaRepository;
  @Autowired
  public IndexRepository indexRepository;
  ModelPage modelPage;
  HashMap<String, Integer> wordsCounter;
  Connection.Response response = null;
  Document doc2 = null;
  public String userAgent;
  public String referrer;
  public String timeout;
  public int siteId;
  String siteName;
  HashMap<String, Integer> finalList;

  public PageIndexingService(List<CrudRepository> repArguments, ConnectionProperties connectionProperties, SitesList initialConSites){
    this.sitesRepository = (SitesRepository) repArguments.get(0);
    this.pagesRepository = (PagesRepository) repArguments.get(1);
    this.indexRepository = (IndexRepository) repArguments.get(2);
    this.lemmaRepository = (LemmaRepository) repArguments.get(3);
    this.connectionProperties=connectionProperties;
    this.initialConSites=initialConSites;
  }

  public Connection.Response Connect (String url){
    userAgent = connectionProperties.getUserAgent();
    referrer = connectionProperties.getReferrer();
    timeout = connectionProperties.getTimeout();

    try {
      response = Jsoup.connect(url)
          .userAgent(userAgent)
          .referrer(referrer)
          .timeout(Integer.parseInt(timeout))
          .execute();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return response;
  }

  public Boolean isPagePresentInRepository(String pageAddress){
    ModelPage modelPage = pagesRepository.findByUrlAndId(pageAddress, siteId);
   if(modelPage==null){
     return false;
   }else{
     return true;
   }
  }

  public Boolean isSitePresentInConfiguration(String siteAddress) {
    Boolean isPresent = false;
    String siteExtractedAddress = gettingExactSiteAddress(siteAddress);
    for (int i = 0; i < initialConSites.getSites().size(); i++) {
      if(siteExtractedAddress.equals(initialConSites.getSites().get(i).getUrl())){
        isPresent = true;
        if(isPresent){
          siteName = initialConSites.getSites().get(i).getName();
        }
        break;
      }
    }
    return isPresent;
  }

  public Boolean isSitePresentInTheRepository(String siteAddress) {
    try {
      siteId = sitesRepository.findIdByUrl(siteAddress);
        return true;
    } catch (Exception e) {
      return false;
    }
  }

  public String gettingExactSiteAddress(String pageAddress){
    String[] steps = pageAddress.split("/");
    String siteAddress = steps[0]+steps[1]+"//"+steps[2];
    return siteAddress;
  }

  public String gettingExactPageAddress(String thisPageAddress){
    String pageAddress = thisPageAddress.replace(gettingExactSiteAddress(thisPageAddress),"");
    return pageAddress;
  }

  public HashMap<String, Integer> sentenceToWords(String sentence){
    wordsCounter = new HashMap<>();
    String[] words = sentence.split("\\.\\s+|\\,*\\s+|\\.\\s*|-+|'|:|\"|\\?|«|»");
    for (int i = 0; i < words.length; i++){
      if(WordProcessor.isServiceWord(words[i])){
        String wordDefaultForm = WordProcessor.getDefaultForm(words[i]);
        if(!wordDefaultForm.isBlank()){
          if(wordsCounter.containsKey(wordDefaultForm)){
            wordsCounter.put(wordDefaultForm,  wordsCounter.get(wordDefaultForm)+1);
          }else{
            wordsCounter.put(wordDefaultForm,1);
          }
        }
      }
    }
    return wordsCounter;
  }

  public void saveSite(Connection.Response response, String pageAddress){
    ModelSite modelSite = new ModelSite();
    modelSite.setUrl(gettingExactSiteAddress(pageAddress));
    modelSite.setName(siteName);
    modelSite.setStatusTime(LocalDateTime.now());
    if(response!=null && response.statusCode()==200) {
      modelSite.setLastError("");
      modelSite.setStatus(Status.INDEXING);
    } else if(response==null){
      modelSite.setStatus(Status.FAILED);
      modelSite.setLastError("Ресурс недоступен");
    } else{
      try {
        modelSite.setLastError(response.statusMessage());
        modelSite.setStatus(Status.FAILED);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    sitesRepository.save(modelSite);
    siteId = sitesRepository.findByName(siteName).getId();
  }

  public void deleteOldEntries(List<Index> index, ModelPage modelPage){
    if(!index.isEmpty()){
      indexRepository.deleteIndexByPageId(modelPage.getId());
      pagesRepository.delete(modelPage);
      finalList.forEach((key, value) -> {
        Lemma lemma = lemmaRepository.findLemmaByLemmaAndSiteId(key,siteId);
        if(lemma!=null){
          if(lemma.getFrequency()>1){
            lemma.setFrequency(lemma.getFrequency()-1);
            lemmaRepository.save(lemma);
          }else{
            lemmaRepository.deleteLemmaByLemmaAndSiteId(key,siteId);
          }
        }
      });
    }
  }

  public void savePage(Connection.Response response, String pageAddress){
    modelPage = new ModelPage();
    if(response!=null && response.statusCode()==200) {
      modelPage.setCode(response.statusCode());
      modelPage.setContent(String.valueOf(doc2));
    }else if(response==null){
      modelPage.setCode(000);
      modelPage.setContent("");
    }else{
      modelPage.setCode(response.statusCode());
      modelPage.setContent(String.valueOf(doc2));
    }
    modelPage.setPath(gettingExactPageAddress(pageAddress));
    modelPage.setModelSite(sitesRepository.findById(siteId).get());
    pagesRepository.save(modelPage);
  }

  public Boolean areLemmasPresent(String pageAddress){
    modelPage = pagesRepository.findByUrlAndId(gettingExactPageAddress(pageAddress),siteId);
    if(modelPage==null){
      return false;
    }
    List<Index> index = indexRepository.findIndexByPageId(modelPage.getId());
    if(!index.isEmpty()){
      return true;
    }
    return false;
  }
  
  public List<Index> findLemmas(String pageAddress){
    modelPage = pagesRepository.findByUrlAndId(gettingExactPageAddress(pageAddress),siteId);
    List<Index> index = indexRepository.findIndexByPageId(modelPage.getId());
    if(!index.isEmpty()){
      return index;
    }
    return new ArrayList<>();
  }

  public synchronized RequestResponse startPageIndexing(String pageAddress) {
    if (!isSitePresentInConfiguration(pageAddress)) {
      return new RequestResponceFailed(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
    } else {
      if (!isSitePresentInTheRepository(gettingExactSiteAddress(pageAddress)) || !isPagePresentInRepository(gettingExactPageAddress(pageAddress)) || (isPagePresentInRepository(gettingExactPageAddress(pageAddress)) && areLemmasPresent(pageAddress))) {
        try {
          response = Connect(pageAddress);
          doc2 = response.parse();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        modelPage = pagesRepository.findByUrlAndId(gettingExactPageAddress(pageAddress), siteId);
        doc2 = Jsoup.parse(modelPage.getContent());
      }
      finalList = sentenceToWords(doc2.body().text());
      if (areLemmasPresent(pageAddress)) {
        List<Index> index = findLemmas(pageAddress);
        if (!index.isEmpty()) {
          deleteOldEntries(index, modelPage);
        }
      }
      if (!isSitePresentInTheRepository(gettingExactSiteAddress(pageAddress))) {
        saveSite(response, pageAddress);
      }
      if (!isPagePresentInRepository(gettingExactPageAddress(pageAddress))) {
        savePage(response, pageAddress);
      }
      if (!finalList.isEmpty()) {
        finalList.forEach(this::savingLemma);
      }
      return new RequestResponceSucceeded(true);
    }
  }

  public void savingLemma(String word, int rank){
    Lemma lemma = lemmaRepository.findLemmaByLemmaAndSiteId(word,siteId);
   if(lemma==null){
     lemma = new Lemma();
     lemma.setLemma(word);
     lemma.setModelSite(sitesRepository.findById(siteId).get());
     lemma.setFrequency(1);
     lemmaRepository.save(lemma);
   }else{
     lemma.setFrequency(lemma.getFrequency()+1);
     lemmaRepository.save(lemma);
   }
    lemma = lemmaRepository.findLemmaByLemmaAndSiteId(word,siteId);
    Index index = new Index();
    index.setRank((float) rank);
    index.setLemma(lemma);
    index.setModelPage(modelPage);
    indexRepository.save(index);
  }
}
