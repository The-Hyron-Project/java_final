package searchengine.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.RequestResponceFailed;
import searchengine.dto.statistics.RequestResponse;
import searchengine.dto.statistics.SearchResponseItem;
import searchengine.dto.statistics.SearchResponseSucceeded;
import searchengine.model.ModelPage;
import searchengine.model.ModelSite;
import searchengine.processors.WordProcessor;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PagesRepository;

@RequiredArgsConstructor
@Service
public class SearchService {
  @Autowired
  private LemmaRepository lemmaRepository;
  @Autowired
  private IndexRepository indexRepository;
  @Autowired
  private PagesRepository pagesRepository;
  private ArrayList<String> separatedWords;
  private Double currentAllowedMaxLemmaFrequencyTotal = (double) 0;
  private HashMap<String, Integer> lemmasWithMaxFrequency;
  private Optional<Entry<String, Integer>> currentLowestFrequencyValue;
  private HashMap<String, ArrayList<ModelPage>> lemmasWithPages;
  private HashMap<Integer, Integer> pageIdsWithMaxRel;
  private HashMap<Integer, Double> pageIdsWithRelRel;
  private HashMap<String, HashMap<String, ArrayList<String>>> lemmasWithPathsAndSnippets;
  private ArrayList<SearchResponseItem> data;

  private HashMap<String, Integer> getEachLemmaMaxFrequency(ArrayList<String> separatedWords){
    HashMap<String, Integer> lemmasWithMaxFrequencyToReturn = new HashMap<>();
    for(int i = 0;separatedWords.size()>i;i++){
      if(lemmaRepository.findLemmaTotalFrequencyByLemma(separatedWords.get(i))!=null){
        List<Integer> numsToSum = lemmaRepository.findLemmaTotalFrequencyByLemma(separatedWords.get(i));
        int sum = numsToSum.stream().reduce(0, (x, y) -> x + y);
        if(sum>0){
          lemmasWithMaxFrequencyToReturn.put(separatedWords.get(i),sum);
        }
      }
    }
      return lemmasWithMaxFrequencyToReturn;
  }

  private ArrayList<String> sentenceToWords(String sentence){
    ArrayList<String> separateWords = new ArrayList<>();
    String[] words = sentence.split("\\.\\s+|\\,*\\s+|\\.\\s*|-+|'|:|\"|\\?|«|»");
      for (int i = 0; i < words.length; i++){
        if(WordProcessor.isServiceWord(words[i])){
          String wordDefaultForm = WordProcessor.getDefaultForm(words[i]);
          if(!wordDefaultForm.isBlank()){
            if(!separateWords.contains(wordDefaultForm)){
              separateWords.add(wordDefaultForm);
            }
          }
        }
      }
    return separateWords;
  }

  private ArrayList<String> lemmaToSnippet(String lemma, String text){
    String[] words = text.split(" ");
    ArrayList<String> snippetLocal = new ArrayList<>(WordProcessor.arrayToSentence(lemma, words));
    return snippetLocal;
  }

  private ArrayList<SearchResponseItem> finishingSearch(Double frequencyTotalLocal, HashMap<Integer, Object> collectingMap){
    ArrayList<SearchResponseItem> dataToReturn = new ArrayList<>();
    HashMap<String, Integer> lemmasWithMaxFrequencyLocal = (HashMap<String, Integer>) collectingMap.get(1);
    for(int i = 0; frequencyTotalLocal>=i;i++){
      for(Entry<String, Integer> entry : lemmasWithMaxFrequencyLocal.entrySet()) {
        if(entry.getValue() == i){
          dataToReturn.addAll(iteratingThroughLemmasWithMaxFrequency(entry, collectingMap));
        }
      }
    }
    return dataToReturn;
  }

  private ArrayList<SearchResponseItem> iteratingThroughLemmasWithMaxFrequency(Entry<String, Integer> entry, HashMap<Integer, Object> collectingMap){
    ArrayList<SearchResponseItem> dataToReturnLocal = new ArrayList<>();
    HashMap<String, ArrayList<ModelPage>> lemmasWithPagesLocal = (HashMap<String, ArrayList<ModelPage>>) collectingMap.get(2);
    for(Entry<String, ArrayList<ModelPage>> entry2 : lemmasWithPagesLocal.entrySet()) {
      if(Objects.equals(entry.getKey(), entry2.getKey())){
        HashMap<Integer, Object> collectingMapLocalLemmasFrequency = new HashMap<>();
        collectingMapLocalLemmasFrequency.put(1, entry);
        collectingMapLocalLemmasFrequency.put(2, entry2);
        dataToReturnLocal.addAll(iteratingThroughLemmasWithPages(collectingMapLocalLemmasFrequency, collectingMap));
      }
    }
    return dataToReturnLocal;
  }

  private ArrayList<SearchResponseItem> iteratingThroughLemmasWithPages(HashMap<Integer, Object> collectingMap1, HashMap<Integer, Object> collectingMap){
    ArrayList<SearchResponseItem> dataToReturnLocal = new ArrayList<>();
    HashMap<String, HashMap<String, ArrayList<String>>> lemmasWithPathsAndSnippetsLocal = (HashMap<String, HashMap<String, ArrayList<String>>>) collectingMap.get(3);
    Entry<String, Integer> entry = (Entry<String, Integer>) collectingMap1.get(1);
    Entry<String, ArrayList<ModelPage>> entry2 = (Entry<String, ArrayList<ModelPage>>) collectingMap1.get(2);
    for(int t=0; entry2.getValue().size()>t;t++){
      for(Entry<String, HashMap<String, ArrayList<String>>> entry3 : lemmasWithPathsAndSnippetsLocal.entrySet()) {
        if(Objects.equals(entry3.getKey(), entry.getKey())){
          int[] counters = new int[2];
          counters[0] = t;
          HashMap<Integer, Object> collectingMapLemmasWithPages = new HashMap<>();
          collectingMapLemmasWithPages.put(1, entry2);
          collectingMapLemmasWithPages.put(2, entry3);
          dataToReturnLocal.addAll(iteratingThroughPathsAndSnippets(counters, collectingMapLemmasWithPages, collectingMap));
        }
      }
    }
    return dataToReturnLocal;
  }

  private ArrayList<SearchResponseItem> iteratingThroughPathsAndSnippets(int[] counters, HashMap<Integer, Object> collectingMap1, HashMap<Integer, Object> collectingMap){
    ArrayList<SearchResponseItem> dataToReturnLocal = new ArrayList<>();
    Entry<String, ArrayList<ModelPage>> entry2 = (Entry<String, ArrayList<ModelPage>>) collectingMap1.get(1);
    Entry<String, HashMap<String, ArrayList<String>>> entry3 = (Entry<String, HashMap<String, ArrayList<String>>>) collectingMap1.get(2);
    for(Entry<String, ArrayList<String>> entry4 : entry3.getValue().entrySet()) {
      if(Objects.equals(entry4.getKey(), entry2.getValue().get(counters[0]).getModelSite().getUrl() + entry2.getValue().get(counters[0]).getPath())){
        for(int u = 0;entry4.getValue().size()>u;u++){
          counters[1] = u;
          HashMap<Integer, Object> collectingMapPathsAndSnippets = new HashMap<>();
          collectingMapPathsAndSnippets.put(1, entry2);
          collectingMapPathsAndSnippets.put(2, entry4);
          dataToReturnLocal.add(formingResponseItem(counters, collectingMapPathsAndSnippets, collectingMap));
        }
      }
    }
    return dataToReturnLocal;
  }

  private SearchResponseItem formingResponseItem(int[] counters, HashMap<Integer, Object> collectingMapPathsAndSnippets, HashMap<Integer, Object> collectingMap){
    Entry<String, ArrayList<ModelPage>> entry2 = (Entry<String, ArrayList<ModelPage>>) collectingMapPathsAndSnippets.get(1);
    Entry<String, ArrayList<String>> entry4 = (Entry<String, ArrayList<String>>) collectingMapPathsAndSnippets.get(2);
    ModelSite modelSite1 = entry2.getValue().get(counters[0]).getModelSite();
    HashMap<Integer, Double> pageIdsWithRelRelLocal = (HashMap<Integer, Double>) collectingMap.get(4);
    return new SearchResponseItem(
        modelSite1.getUrl(),
        modelSite1.getName(),
        entry2.getValue().get(counters[0]).getPath(),
        Jsoup.parse(entry2.getValue().get(counters[0]).getContent()).title(),
        entry4.getValue().get(counters[1]),
        pageIdsWithRelRelLocal.get(entry2.getValue().get(counters[0]).getId()));
  }

  private HashMap<Integer, Integer> calculatePageIdsWithMaxRel(Double maxLemmaFrequency, HashMap<String, Integer> lemmasWithMaxFrequency){
    HashMap<Integer, Integer> mapToReturn = new HashMap<>();
    for(int i = 0; maxLemmaFrequency>=i;i++) {
      for (Entry<String, Integer> entry : lemmasWithMaxFrequency.entrySet()) {
        if (entry.getValue() == i) {
          List<Integer> lemmaIds = lemmaRepository.findLemmaIdByLemma(entry.getKey());
          mapToReturn.putAll(findPageIdByLemmaId(lemmaIds));
        }
      }
    }
    return mapToReturn;
  }

  private HashMap<Integer, Integer> collectIdsAndRankSum(List<Integer> pageIdsNoRelLocal){
    HashMap<Integer, Integer> mapToReturn = new HashMap<>();
    for (int p = 0; pageIdsNoRelLocal.size() > p; p++) {
      List<Integer> indexRanks = indexRepository.findAllRanksByPageId(pageIdsNoRelLocal.get(p));
      int rankSum = indexRanks.stream().mapToInt(Integer::intValue).sum();
      mapToReturn.put(pageIdsNoRelLocal.get(p), rankSum);
    }
    return mapToReturn;
  }

  private HashMap<Integer, Integer> findPageIdByLemmaId(List<Integer> lemmaIdsLocal){
    HashMap<Integer, Integer> mapToReturn = new HashMap<>();
    for (int z = 0; lemmaIdsLocal.size() > z; z++) {
      List<Integer> pageIdsNoRel = indexRepository.findPageIdByLemmaId(lemmaIdsLocal.get(z));
      mapToReturn.putAll(collectIdsAndRankSum(pageIdsNoRel));
    }
    return mapToReturn;
  }

  private HashMap<String, ArrayList<ModelPage>> mapLemmasToPages(String site, Double maxLemmaFrequencyTotal, HashMap<String, Integer> lemmasWithMaxFrequencyLocal){
    HashMap<String, ArrayList<ModelPage>> lemmasWithPagesToReturn = new HashMap<>();
    int pagesArrayReduction = 0;
    int pagesCounter = 0;
    for(int i = 0; maxLemmaFrequencyTotal>=i;i++){
      for(Entry<String, Integer> entry : lemmasWithMaxFrequencyLocal.entrySet()) {
        if(entry.getValue() == i){
          List<Integer> lemmaIds = lemmaRepository.findLemmaIdByLemma(entry.getKey());
          ArrayList<Integer> pagesIds = new ArrayList<>(getPageIds(lemmaIds));
          int pagesCounterToIterate = calculatePagesCounter(pagesCounter, pagesIds.size(), pagesArrayReduction);
          ArrayList<ModelPage> pages = new ArrayList<>(getListOfPages(pagesIds, pagesCounterToIterate, site));
          lemmasWithPagesToReturn.put(entry.getKey(), pages);
          pagesCounter = calculatePagesCounterChange(pagesCounter, pages.size());
          pagesArrayReduction++;
        }
      }
    }
    return lemmasWithPagesToReturn;
  }

  private int calculatePagesCounterChange(int pagesCounterLocal, int size){
    if(pagesCounterLocal-1<size){
      return pagesCounterLocal-1;
    }else{
      return size;
    }
  }

  private ArrayList<ModelPage> getListOfPages(ArrayList<Integer> pagesIdsLocal, int pagesCounterToIterateLocal, String siteLocal){
    ArrayList<ModelPage> pagesLocal = new ArrayList<>();
    for(int z = 0;pagesCounterToIterateLocal>z;z++){
      ModelPage modelPageToCheck = pagesRepository.findById(pagesIdsLocal.get(z)).get();
      if(siteLocal!=null && !(siteLocal.isEmpty())){
        if(Objects.equals(modelPageToCheck.getModelSite().getUrl(), siteLocal)){
          pagesLocal.add(modelPageToCheck);
        }
      }else{
        pagesLocal.add(modelPageToCheck);
      }
    }
    return pagesLocal;
  }

  private int calculatePagesCounter(int pagesCounterLocal, int size, int pagesArrayReductionLocal){
    if(pagesCounterLocal!=0 && size>pagesCounterLocal){
      return size-pagesArrayReductionLocal;
    }else{
      return size;
    }
  }

  private ArrayList<Integer> getPageIds(List<Integer> lemmaIdsLocal){
    ArrayList<Integer> pagesIdsToReturn = new ArrayList<>();
    for(int z = 0;lemmaIdsLocal.size()>z;z++){
      List<Integer> pageIdsNoRel = indexRepository.findPageIdByLemmaId(lemmaIdsLocal.get(z));
      pagesIdsToReturn.addAll(pageIdsNoRel);
    }
    return pagesIdsToReturn;
  }

  private HashMap<String, HashMap<String, ArrayList<String>>> mapLemmasToPathsAndSnippets(HashMap<String, ArrayList<ModelPage>> lemmasWithPagesLocal){
    HashMap<String, HashMap<String, ArrayList<String>>> lemmasWithPathsAndSnippetsToReturn = new HashMap<>();
    for(Entry<String, ArrayList<ModelPage>> entry : lemmasWithPagesLocal.entrySet()){
      ArrayList<String> textArray;
      HashMap<String, ArrayList<String>> dataToInsert  = new HashMap<>();;
      String path = "";
      for(int i = 0;entry.getValue().size()>i;i++){
        textArray = new ArrayList<>();
        ModelSite modelSite = entry.getValue().get(i).getModelSite();
        Document docLocal = Jsoup.parse(entry.getValue().get(i).getContent());
        path=modelSite.getUrl()+entry.getValue().get(i).getPath();
        textArray.addAll(lemmaToSnippet(entry.getKey(), docLocal.body().text()));
        dataToInsert.put(path, textArray);
      }
      lemmasWithPathsAndSnippetsToReturn.put(entry.getKey(), dataToInsert);
    }
    return lemmasWithPathsAndSnippetsToReturn;
  }

  private int calculateOffset(int offset){
    if(offset>=data.size()){
      offset=0;
    }
    return offset;
  }

  private int calculateLimit(int localLimit, int localoffset, int localSize){
    if(localLimit+localoffset>localSize){
      localLimit=localSize;
    }else{
    localLimit=localLimit+localoffset;
    }
    return localLimit;
  }

  private Optional calculateLowestFrequency(HashMap<String, Integer> lemmasWithMaxFrequency){
    if(!lemmasWithMaxFrequency.isEmpty()){
      Optional localCurrentLowestFrequencyValue = lemmasWithMaxFrequency.entrySet()
          .stream()
          .min((Entry<String, Integer> e1, Entry<String, Integer> e2) -> e1.getValue()
              .compareTo(e2.getValue())
          );
      return localCurrentLowestFrequencyValue;
    }
    return Optional.empty();
  }

  private Double calculateAllowedMaxLemmaFrequencyTotal(Optional<Entry<String, Integer>> currentLowestFrequencyValue, int size){
    if(currentLowestFrequencyValue!=null && currentLowestFrequencyValue.isPresent()) {
      return  (double) (currentLowestFrequencyValue.get().getValue()
          * size);
    }
    return (double) 0;
  }

  private HashMap<Integer, Double> calculatePageIdsWithRelRel(HashMap<Integer, Integer> pageIdsWithMaxRel, Double maxRel){
    HashMap<Integer, Double> localMap = new HashMap<>();
    for(Entry<Integer, Integer> entry : pageIdsWithMaxRel.entrySet()) {
      localMap.put(entry.getKey(), entry.getValue()/maxRel);
    }
    return localMap;
  }

  private Double calculateMaxRel(HashMap<Integer, Integer> pageIdsWithMaxRel){
   return Double.valueOf(pageIdsWithMaxRel.entrySet().stream().max((Entry<Integer, Integer> e1, Entry<Integer, Integer> e2) -> e1.getValue().compareTo(e2.getValue())).get().getValue());
  }

  public RequestResponse startSearching(String sentence, int offset, int limit, String site){
    if(sentence==null || sentence.isEmpty()){
      return new RequestResponceFailed(false, "Задан пустой поисковый запрос");
    }
    separatedWords = sentenceToWords(sentence);
    lemmasWithMaxFrequency = getEachLemmaMaxFrequency(separatedWords);
    if(lemmasWithMaxFrequency.isEmpty()){
      return new SearchResponseSucceeded(true, 0,
          new ArrayList<>());
    }
    currentLowestFrequencyValue = calculateLowestFrequency(lemmasWithMaxFrequency);
    currentAllowedMaxLemmaFrequencyTotal = calculateAllowedMaxLemmaFrequencyTotal(currentLowestFrequencyValue, lemmasWithMaxFrequency.size());
    lemmasWithMaxFrequency.entrySet().removeIf(entry -> entry.getValue()>currentAllowedMaxLemmaFrequencyTotal);
    pageIdsWithMaxRel = new HashMap<>(calculatePageIdsWithMaxRel(currentAllowedMaxLemmaFrequencyTotal, lemmasWithMaxFrequency));
    lemmasWithPages = new HashMap<>(mapLemmasToPages(site, currentAllowedMaxLemmaFrequencyTotal, lemmasWithMaxFrequency));
    pageIdsWithRelRel = new HashMap<>(calculatePageIdsWithRelRel(pageIdsWithMaxRel, calculateMaxRel(pageIdsWithMaxRel)));
    lemmasWithPathsAndSnippets = new HashMap<>(mapLemmasToPathsAndSnippets(lemmasWithPages));
    HashMap<Integer, Object> collectingMap = new HashMap<>();
    collectingMap.put(1, lemmasWithMaxFrequency);
    collectingMap.put(2, lemmasWithPages);
    collectingMap.put(3, lemmasWithPathsAndSnippets);
    collectingMap.put(4, pageIdsWithRelRel);
    data = new ArrayList<>(finishingSearch(currentAllowedMaxLemmaFrequencyTotal, collectingMap));
    return new SearchResponseSucceeded(true, data.size(),
        new ArrayList<>(data.subList(calculateOffset(offset), calculateLimit(limit, calculateOffset(offset), data.size()))));
  }
}

