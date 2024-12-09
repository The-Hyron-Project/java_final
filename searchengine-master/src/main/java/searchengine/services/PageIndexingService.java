package searchengine.services;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Service
public class PageIndexingService {
  private final PagesRepository pagesRepository;
  private final SitesRepository sitesRepository;
  private final ConnectionProperties connectionProperties;
  private final SitesList initialConSites;
  private final LemmaRepository lemmaRepository;
  private final IndexRepository indexRepository;
  private ModelPage modelPage;
  private Connection.Response response = null;
  private Document doc2 = null;
  private String userAgent;
  private String referrer;
  private String timeout;
  private int siteId;
  private String siteName;
  private HashMap<String, Integer> finalList;
  private List<Thread> subTasks = new ArrayList<>();
  private HashMap<String, Integer> wordsCounter;
  private static final Object mutex = new Object();

  public PageIndexingService(List<CrudRepository> repArguments, ConnectionProperties connectionProperties, SitesList initialConSites){
    this.sitesRepository = (SitesRepository) repArguments.get(0);
    this.pagesRepository = (PagesRepository) repArguments.get(1);
    this.indexRepository = (IndexRepository) repArguments.get(2);
    this.lemmaRepository = (LemmaRepository) repArguments.get(3);
    this.connectionProperties=connectionProperties;
    this.initialConSites=initialConSites;
  }

  private Connection.Response Connect (String url){
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

  private Boolean isPagePresentInRepository(String pageAddress){
    ModelPage modelPage = pagesRepository.findByUrlAndId(pageAddress, siteId);
   if(modelPage==null){
     return false;
   }else{
     return true;
   }
  }

  private Boolean isSitePresentInConfiguration(String siteAddress) {
    Boolean isPresent = false;
    String siteExtractedAddress = gettingExactSiteAddress(siteAddress);
    for (int i = 0; i < initialConSites.getSites().size(); i++) {
      isPresent = gettingSiteName(siteExtractedAddress, i);
      if(isPresent) break;
    }
    return isPresent;
  }

  private boolean gettingSiteName(String siteExtractedAddress, int i){
    Boolean isPresentLocal = false;
    if(siteExtractedAddress.equals(initialConSites.getSites().get(i).getUrl())){
      isPresentLocal = true;
      siteName = initialConSites.getSites().get(i).getName();
    }
    return isPresentLocal;
  }

  private Boolean isSitePresentInTheRepository(String siteAddress) {
    try {
      siteId = sitesRepository.findIdByUrl(siteAddress);
        return true;
    } catch (Exception e) {
      return false;
    }
  }

  private String gettingExactSiteAddress(String pageAddress){
    String[] steps = pageAddress.split("/");
    String siteAddress = steps[0]+steps[1]+"//"+steps[2];
    return siteAddress;
  }

  private String gettingExactPageAddress(String thisPageAddress){
    String pageAddress = thisPageAddress.replace(gettingExactSiteAddress(thisPageAddress),"");
    return pageAddress;
  }

  private HashMap<String, Integer> sentenceToWords(String sentence){
    wordsCounter = new HashMap<>();
    String[] words = sentence.split("\\.\\s+|\\,*\\s+|\\.\\s*|-+|'|:|\"|\\?|«|»");
    if(words.length<6){
      return sentenceToWordsSingleThread(words, 0, words.length);
    }else{
      return sentenceToWordsMultiThread(words);
    }
  }

  private HashMap<String, Integer> sentenceToWordsSingleThread(String[] words, int start, int finish) {
    for (; start < finish; start++) {
      if (WordProcessor.isServiceWord(words[start])) {
        savingDefaultForm(words[start]);
      }
    }
    return wordsCounter;
  }

  private void savingDefaultForm(String wordToFind){
      String wordDefaultForm = WordProcessor.getDefaultForm(wordToFind);
      if(!wordDefaultForm.isBlank()){
        synchronized(this){
          if(wordsCounter.containsKey(wordDefaultForm)){
            wordsCounter.put(wordDefaultForm, wordsCounter.get(wordDefaultForm) + 1);
          }else{
            wordsCounter.put(wordDefaultForm,1);
          }
        }
      }
    }

  private HashMap<String, Integer> sentenceToWordsMultiThread(String[] words){
    Thread PageIndexingThread = new Thread(
        () -> sentenceToWordsSingleThread(words, 0, words.length/3));
    Thread PageIndexingThread2 = new Thread(
        () -> sentenceToWordsSingleThread(words, words.length/3, words.length/3*2));
    Thread PageIndexingThread3 = new Thread(
        () -> sentenceToWordsSingleThread(words, words.length/3*2, words.length));
    subTasks.add(PageIndexingThread);
    PageIndexingThread.start();
    subTasks.add(PageIndexingThread2);
    PageIndexingThread2.start();
    subTasks.add(PageIndexingThread3);
    PageIndexingThread3.start();
    for (Thread task : subTasks) {
      try {
        task.join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    return wordsCounter;
  }

  private void saveSite(Connection.Response response, String pageAddress){
    log.info("Сохраняем сайт " + pageAddress);
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

  private void deleteOldEntries(List<Index> index, ModelPage modelPage){
    log.info("Удаляем старые леммы");
    if(!index.isEmpty()){
      indexRepository.deleteIndexByPageId(modelPage.getId());
      pagesRepository.delete(modelPage);
      correctingAndDeletingLemmas();
    }
  }

  private void correctingAndDeletingLemmas(){
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

  private void savePage(Connection.Response response, String pageAddress){
    log.info("Сохраняем страницу " + pageAddress);
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

  private Boolean areLemmasPresent(String pageAddress){
    log.info("Проверка наличия лемм для " + pageAddress);
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

  private List<Index> findLemmas(String pageAddress){
    log.info("Ищем леммы для " + pageAddress);
    modelPage = pagesRepository.findByUrlAndId(gettingExactPageAddress(pageAddress),siteId);
    List<Index> index = indexRepository.findIndexByPageId(modelPage.getId());
    if(!index.isEmpty()){
      return index;
    }
    return new ArrayList<>();
  }
  
  public RequestResponse startPageIndexing(String pageAddress) {
    if (!isSitePresentInConfiguration(pageAddress)) {
      return new RequestResponceFailed(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
    }
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
    findingAndDeletingOldEntries(pageAddress);
    savingData(response, pageAddress);
    if (!finalList.isEmpty()) {
        finalList.forEach(this::savingFinalLemma);
    }
    return new RequestResponceSucceeded(true);
  }

  private void findingAndDeletingOldEntries(String pageAddress){
    if (areLemmasPresent(pageAddress)) {
      List<Index> index = findLemmas(pageAddress);
      if (!index.isEmpty()) {
        deleteOldEntries(index, modelPage);
      }
    }
  }

  private void savingData(Connection.Response response, String pageAddress){
    if (!isSitePresentInTheRepository(gettingExactSiteAddress(pageAddress))) {
      saveSite(response, pageAddress);
    }
    if (!isPagePresentInRepository(gettingExactPageAddress(pageAddress))) {
      savePage(response, pageAddress);
    }
  }

  private void savingFinalLemma(String word, int rank){
    synchronized(mutex){
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
    }
    Lemma lemmaSaved = lemmaRepository.findLemmaByLemmaAndSiteId(word,siteId);
    Index index = new Index();
    index.setRank((float) rank);
    index.setLemma(lemmaSaved);
    index.setModelPage(modelPage);
    indexRepository.save(index);
  }
}
