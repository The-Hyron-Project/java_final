package searchengine.services;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.ConnectionProperties;
import searchengine.config.SitesList;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.ModelPage;
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

//  ModelPage modelPage;
HashMap<String, Integer> finalList;
  HashMap<String, Integer> wordsCounter;
  List<String> serviceWords = Arrays.asList(" СОЮЗ "," ЧАСТ", " ПРЕДЛ") ;
//  Connection.Response response = null;
//  Document doc2 = null;
  Document doc3 = null;

//  public String userAgent;
//  public String referrer;
//  public String timeout;

  public int siteId;
//
//      try {
//    response = Jsoup.connect("http://localhost")
//        .execute();
//  } catch (Exception e) {
//  }
//
//  doc2 = response.parse();
//    System.out.println(doc2.text());

//      HashMap<String, Integer> finalList =  sentenceToWords(sentence);
//    finalList.forEach((k,v) -> System.out.println(k+" "+v));

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
    for (int i = 0; i < initialConSites.getSites().size(); i++) {
      if(siteAddress.equals(initialConSites.getSites().get(i).getUrl())){
        isPresent = true;
        break;
      }
    }
    return isPresent;
  }

  public Boolean isSitePresentInTheRepository(String siteAddress) {
    siteId = sitesRepository.findIdByUrl(siteAddress);
    if(siteId!=0){
      return true;
    }else{
      return false;
    }
  }

  public String gettingSiteAddress(String pageAddress){
    String[] steps = pageAddress.split("/");
    String siteAddress = steps[0]+steps[1]+"//"+steps[2];
    return siteAddress;
  }

  public String gettingExactPageAddress(String thisPageAddress){
    String pageAddress = thisPageAddress.replace(gettingSiteAddress(thisPageAddress),"");
    return pageAddress;
  }

  public HashMap<String, Integer> sentenceToWords(String sentence){
    wordsCounter = new HashMap<>();
    String[] words = sentence.split("\\.\\s+|\\,*\\s+|\\.\\s*");
    try{
      LuceneMorphology luceneMorph = new RussianLuceneMorphology();
      for (int i = 0; i < words.length; i++){
        if(words[i].matches("\\D*")){
          if
          (!luceneMorph.getMorphInfo(words[i].toLowerCase()).toString().contains(serviceWords.get(0))
              && !luceneMorph.getMorphInfo(words[i].toLowerCase()).toString().contains(serviceWords.get(1))
              && !luceneMorph.getMorphInfo(words[i].toLowerCase()).toString().contains(serviceWords.get(2))
          )
          {
            if(wordsCounter.containsKey(words[i].toLowerCase())){
              wordsCounter.put(luceneMorph.getNormalForms(words[i].toLowerCase()).get(0),wordsCounter.get(words[i].toLowerCase())+1);
            }else{
              wordsCounter.put(luceneMorph.getNormalForms(words[i].toLowerCase()).get(0),1);
            }
          }
        }
      }
    }catch (Exception e) {
      }
    wordsCounter.size();
    return wordsCounter;
  }

  public Boolean startPageIndexing(String pageAddress){
    if(isSitePresentInTheRepository(gettingSiteAddress(pageAddress))){
     modelPage = pagesRepository.findByUrlAndId(gettingExactPageAddress(pageAddress),siteId);
      doc3 = Jsoup.parse(modelPage.getContent());
      finalList = sentenceToWords(doc3.body().text());
    }
    indexRepository.deleteIndexByPageId(modelPage.getId());
    finalList.forEach((key, value) -> lemmaRepository.deleteLemmaByLemmaAndSiteId(key,siteId));
    finalList.forEach(this::savingLemma);
    return true;
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
