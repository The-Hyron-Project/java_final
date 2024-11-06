package searchengine.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.RequestResponceSucceeded;
import searchengine.dto.statistics.RequestResponse;
import searchengine.model.ModelPage;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PagesRepository;

@RequiredArgsConstructor
@Service
public class SearchService {
  @Autowired
  public LemmaRepository lemmaRepository;
  @Autowired
  public IndexRepository indexRepository;
  @Autowired
  public PagesRepository pagesRepository;
  ArrayList<String> separatedWords;
  List<String> serviceWords = Arrays.asList(" СОЮЗ "," ЧАСТ", " ПРЕДЛ", " МЕЖД");
  Double currentAllowedMaxLemmaFrequencyTotal = (double) 0;
  Double division = 0.75;
  HashMap<String, Integer> lemmasWithRank;
  Optional<Entry<String, Integer>> currentMaxFrequencyValue;
  RequestResponceSucceeded requestResponceSucceeded;
  HashMap<String, ArrayList<ModelPage>> lemmasWithPages;
  ArrayList<ModelPage> pages;
  HashMap<Integer, Integer> pageIdsWithMaxRel;
  HashMap<Integer, Double> pageIdsWithRelRel;

//  public ArrayList<ModelPage> findPagesForLemma(ArrayList<String> separatedWords){
//    HashMap<String, ModelPage> lemmasWithPages = new HashMap<>();
//    Optional foundPage;
//    for(int i = 0; separatedWords.size()>i;i++){
//
//    }
//  }

  public HashMap<String, Integer> getEachLemmaMaxRank(ArrayList<String> separatedWords){
    HashMap<String, Integer> lemmasWithRankToReturn = new HashMap<>();
    for(int i = 0;separatedWords.size()>i;i++){
      if(lemmaRepository.findLemmaTotalFrequencyByLemma(separatedWords.get(i))!=null){
        List<Integer> numsToSum = lemmaRepository.findLemmaTotalFrequencyByLemma(separatedWords.get(i));
        int sum = numsToSum.stream().reduce(0, (x, y) -> x + y);
        lemmasWithRankToReturn.put(separatedWords.get(i),sum);
      }
    }
      return lemmasWithRankToReturn;
  }

//  public Double getCurrentAllowedMaxLemmaFrequencyTotal(){
//    return indexRepository.findMaxTotalAllLemmasRank()*division;
//  }

  public ArrayList<String> sentenceToWords(String sentence){
    ArrayList<String> separateWords = new ArrayList<>();
//    ArrayList<String> brokenWords = new ArrayList<>();
    String[] words = sentence.split("\\.\\s+|\\,*\\s+|\\.\\s*|-+|'|:|\"|\\?|«|»");
    try{
      LuceneMorphology luceneMorph = new RussianLuceneMorphology();
      for (int i = 0; i < words.length; i++){
        if(words[i].matches("\\D*") && !words[i].isBlank() ){
          try{
            if
            (!luceneMorph.getMorphInfo(words[i].toLowerCase()).toString().contains(serviceWords.get(0))
                && !luceneMorph.getMorphInfo(words[i].toLowerCase()).toString().contains(serviceWords.get(1))
                && !luceneMorph.getMorphInfo(words[i].toLowerCase()).toString().contains(serviceWords.get(2))
                && !luceneMorph.getMorphInfo(words[i].toLowerCase()).toString().contains(serviceWords.get(3))
            )
            {
              if(!separateWords.contains(words[i].toLowerCase())){
                separateWords.add(luceneMorph.getNormalForms(words[i].toLowerCase()).get(0));
              }
            }
          }catch (Exception e) {
//            System.out.println(words[i] + " не подошло");
//            System.out.println(luceneMorph.getNormalForms(words[i].toLowerCase()));
//            brokenWords.add(words[i]);
          }
        }
      }
    }catch (Exception e) {
    }
    return separateWords;
  }

  public RequestResponse startSearching(String sentence){
    separatedWords = sentenceToWords(sentence);
    lemmasWithRank = getEachLemmaMaxRank(separatedWords);
    pageIdsWithMaxRel = new HashMap<>();
    pageIdsWithRelRel = new HashMap<>();
    if(!lemmasWithRank.isEmpty()){
      currentMaxFrequencyValue = lemmasWithRank.entrySet()
          .stream()
          .max((Entry<String, Integer> e1, Entry<String, Integer> e2) -> e1.getValue()
              .compareTo(e2.getValue())
          );
    }
    if(currentMaxFrequencyValue!=null && currentMaxFrequencyValue.isPresent()){
      currentAllowedMaxLemmaFrequencyTotal = currentMaxFrequencyValue.get().getValue()*division;
    }
    lemmasWithPages = new HashMap<>();
    for(int i = 0; currentAllowedMaxLemmaFrequencyTotal>i;i++){
      for(Map.Entry<String, Integer> entry : lemmasWithRank.entrySet()) {
        if(entry.getValue() == i){
          List<Integer> lemmaIds = lemmaRepository.findLemmaIdByLemma(entry.getKey());
          ArrayList<Integer> pagesIds = new ArrayList<>();
          for(int z = 0;lemmaIds.size()>z;z++){
            List<Integer> pageIdsNoRel = indexRepository.findPageIdByLemmaId(lemmaIds.get(z));
            pagesIds.addAll(pageIdsNoRel);
            for(int p=0;pageIdsNoRel.size()>p;p++){
              List<Integer> indexRanks = indexRepository.findAllRanksByPageId(pageIdsNoRel.get(p));
              int rankSum = indexRanks.stream().mapToInt(Integer::intValue).sum();
              //    ищем абсолютную релевантность для каждой страницы
              pageIdsWithMaxRel.put(pageIdsNoRel.get(p), rankSum);
            }
          }
          pages = new ArrayList<>();
          for(int z = 0;pagesIds.size()>z;z++){
            pages.add(pagesRepository.findById(pagesIds.get(z)).get());
          }
          lemmasWithPages.put(entry.getKey(), pages);
        }
      }
    }
    Double maxRel = Double.valueOf(pageIdsWithMaxRel.entrySet()
        .stream()
        .max((Entry<Integer, Integer> e1, Entry<Integer, Integer> e2) -> e1.getValue()
            .compareTo(e2.getValue())
        ).get().getValue());
    
    for(Entry<Integer, Integer> entry : pageIdsWithMaxRel.entrySet()) {
      pageIdsWithRelRel.put(entry.getKey(), entry.getValue()/maxRel);
    }

    return requestResponceSucceeded = new RequestResponceSucceeded(true);
  }
}
