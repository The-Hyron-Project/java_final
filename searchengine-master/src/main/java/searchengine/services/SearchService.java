package searchengine.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
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
import searchengine.repositories.SitesRepository;

@Slf4j
@RequiredArgsConstructor
@Service
public class SearchService {

  private final LemmaRepository lemmaRepository;
  private final IndexRepository indexRepository;
  private final PagesRepository pagesRepository;
  private final SitesRepository sitesRepository;
  private ArrayList<String> separatedWords;
  private Double currentAllowedMaxLemmaFrequencyTotal = (double) 0;
  private HashMap<String, Integer> lemmasWithMaxFrequency;
  private Optional<Entry<String, Integer>> currentLowestFrequencyValue;
  private HashMap<Integer, Double> pageIdsWithMaxRel;
  private HashMap<Integer, Double> pageIdsWithRelRel;
  private ArrayList<SearchResponseItem> data;
  private ArrayList<String> separateWords;
  private List<Thread> subTasks = new ArrayList<>();
  private ArrayList<Integer> foundPageIds;
  private ArrayList<String> lemmas;
  private ArrayList<Integer> lemmaIds;

  private HashMap<String, Integer> getEachLemmaMaxFrequency(ArrayList<String> separatedWords,
      String site) {
    HashMap<String, Integer> lemmasWithMaxFrequencyToReturn = new HashMap<>();
    if (site == null) {
      lemmasWithMaxFrequencyToReturn.putAll(processLemmasNoFilter(separatedWords));
    } else {
      int localSiteId = sitesRepository.findIdByUrl(site);
      lemmasWithMaxFrequencyToReturn.putAll(processLemmasWithFilter(separatedWords, localSiteId));
    }
    return lemmasWithMaxFrequencyToReturn;
  }

  private HashMap<String, Integer> processLemmasNoFilter(ArrayList<String> separatedWords) {
    HashMap<String, Integer> lemmasWithMaxFrequencyToReturn = new HashMap<>();
    for (int i = 0; separatedWords.size() > i; i++) {
      HashMap<String, Integer> localCheck = new HashMap<>(
          getLemmaMaxFrequencyNoFiltration(separatedWords.get(i)));
      if (!localCheck.containsKey("none")) {
        lemmasWithMaxFrequencyToReturn.putAll(localCheck);
      } else {
        return new HashMap<>();
      }
    }
    return lemmasWithMaxFrequencyToReturn;
  }

  private HashMap<String, Integer> getLemmaMaxFrequencyNoFiltration(String word) {
    HashMap<String, Integer> localMapToReturn = new HashMap<>();
    if (lemmaRepository.findLemmaTotalFrequencyByLemma(word) != null) {
      List<Integer> numsToSum = lemmaRepository.findLemmaTotalFrequencyByLemma(word);
      int sum = numsToSum.stream().reduce(0, (x, y) -> x + y);
      localMapToReturn.put(word, sum);
    } else {
      localMapToReturn.put("none", 0);
    }
    return localMapToReturn;
  }

  private HashMap<String, Integer> processLemmasWithFilter(ArrayList<String> separatedWords,
      int localSiteId) {
    HashMap<String, Integer> lemmasWithMaxFrequencyToReturn = new HashMap<>();
    for (int i = 0; separatedWords.size() > i; i++) {
      HashMap<String, Integer> localCheck = new HashMap<>(
          getLemmaMaxFrequencyWithFiltration(separatedWords.get(i), localSiteId));
      if (!localCheck.containsValue(0)) {
        lemmasWithMaxFrequencyToReturn.putAll(localCheck);
      } else {
        return new HashMap<>();
      }
    }
    return lemmasWithMaxFrequencyToReturn;
  }

  private HashMap<String, Integer> getLemmaMaxFrequencyWithFiltration(String word,
      int localSiteId) {
    HashMap<String, Integer> localMapToReturn = new HashMap<>();
    if (lemmaRepository.findLemmaTotalFrequencyByLemma(word) != null) {
      List<Integer> numsToSum = lemmaRepository.findLemmaTotalFrequencyByLemmaAndSiteId(word,
          localSiteId);
      int sum = numsToSum.stream().reduce(0, (x, y) -> x + y);
      localMapToReturn.put(word, sum);
    } else {
      localMapToReturn.put("none", 0);
    }
    return localMapToReturn;
  }

  private ArrayList<String> sentenceToWords(String sentence) {
    separateWords = new ArrayList<>();
    String[] words = sentence.split("\\.\\s+|\\,*\\s+|\\.\\s*|-+|'|:|\"|\\?|«|»");
    if (words.length < 6) {
      return sentenceToWordsSingleThread(words, 0, words.length);
    } else {
      return sentenceToWordsMultiThread(words);
    }
  }

  private ArrayList<String> sentenceToWordsSingleThread(String[] words, int start, int finish) {
    for (; start < finish; start++) {
      if (WordProcessor.isNotServiceWord(words[start])) {
        gatherWordDefaultForms(words, start);
      }
    }
    return separateWords;
  }

  private void gatherWordDefaultForms(String[] words, int start) {
    String wordDefaultForm = WordProcessor.getDefaultForm(words[start]);
    if (!wordDefaultForm.isBlank()) {
      if (!separateWords.contains(wordDefaultForm)) {
        separateWords.add(wordDefaultForm.replace('Ё','Е').replace('ё','е'));
      }
    }
  }

  public ArrayList<String> sentenceToWordsMultiThread(String[] words) {
    createThreads(words, 0, words.length / 3);
    createThreads(words, words.length / 3, words.length / 3 * 2);
    createThreads(words, words.length / 3 * 2, words.length);
    joinThreads();
    return separateWords;
  }

  private void createThreads(String[] words, int start, int finish) {
    Thread PageIndexingThread = new Thread() {
      public void run() {
        sentenceToWordsSingleThread(words, start, finish);
      }
    };
    subTasks.add(PageIndexingThread);
    PageIndexingThread.start();
  }

  private void joinThreads() {
    for (Thread task : subTasks) {
      try {
        task.join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private String lemmaToSnippet(ArrayList<String> lemmas, String text) {
    return WordProcessor.arrayToSentence(lemmas, text);
  }
  
  private HashMap<Integer, Double> collectIdsAndRankSum(List<Integer> pageIdsNoRelLocal) {
    HashMap<Integer, Double> mapToReturn = new HashMap<>();
      List<String> localListMap = indexRepository.findSumRanksByPageIdList(pageIdsNoRelLocal, lemmaIds);
      for(int i = 0; localListMap.size()>i;i++) {
        String[] dataToInsert = localListMap.get(i).split(",");
        mapToReturn.put(Integer.parseInt(dataToInsert[0]), Double.parseDouble(dataToInsert[1]));
      }
    return mapToReturn;
  }

  private ArrayList<Integer> getPageIds(List<Integer> lemmaIdsLocal) {
    ArrayList<Integer> pagesIdsToReturn = new ArrayList<>();
    for (int z = 0; lemmaIdsLocal.size() > z; z++) {
      List<Integer> pageIdsNoRel = indexRepository.findPageIdByLemmaId(lemmaIdsLocal.get(z));
      pagesIdsToReturn.addAll(pageIdsNoRel);
    }
    return pagesIdsToReturn;
  }

  private int calculateOffset(int offset) {
    if (offset >= pageIdsWithRelRel.size()) {
      offset = 0;
    }
    return offset;
  }

  private Optional calculateLowestFrequency(HashMap<String, Integer> lemmasWithMaxFrequency) {
    if (!lemmasWithMaxFrequency.isEmpty()) {
      Optional localCurrentLowestFrequencyValue = lemmasWithMaxFrequency.entrySet()
          .stream()
          .min((Entry<String, Integer> e1, Entry<String, Integer> e2) -> e1.getValue()
              .compareTo(e2.getValue())
          );
      return localCurrentLowestFrequencyValue;
    }
    return Optional.empty();
  }

  private Double calculateAllowedMaxLemmaFrequencyTotal(
      Optional<Entry<String, Integer>> currentLowestFrequencyValue, int size) {
    if (currentLowestFrequencyValue != null && currentLowestFrequencyValue.isPresent()) {
      return (double) (currentLowestFrequencyValue.get().getValue() * 3);
    }
    return (double) 0;
  }

  private HashMap<Integer, Double> calculatePageIdsWithRelRel(
      HashMap<Integer, Double> pageIdsWithMaxRel, Double maxRel) {
    HashMap<Integer, Double> localMap = new HashMap<>();
    for (Entry<Integer, Double> entry : pageIdsWithMaxRel.entrySet()) {
      localMap.put(entry.getKey(), entry.getValue() / maxRel);
    }
    return localMap;
  }

  private Double calculateMaxRel(HashMap<Integer, Double> pageIdsWithMaxRel) {
    return pageIdsWithMaxRel.entrySet().stream().max(
        (Entry<Integer, Double> e1, Entry<Integer, Double> e2) -> e1.getValue()
            .compareTo(e2.getValue())).get().getValue();
  }

  public RequestResponse startSearching(String sentence, int offset, int limit, String site) {
    if (sentence == null || sentence.isEmpty()) {
      return new RequestResponceFailed(false, "Задан пустой поисковый запрос");
    }
    separatedWords = sentenceToWords(sentence);
    lemmaIds = new ArrayList<>();
    lemmasWithMaxFrequency = getEachLemmaMaxFrequency(separatedWords, site);
    if (lemmasWithMaxFrequency.isEmpty()) {
      return new SearchResponseSucceeded(true, 0, new ArrayList<>());
    }
    currentLowestFrequencyValue = calculateLowestFrequency(lemmasWithMaxFrequency);
    currentAllowedMaxLemmaFrequencyTotal = calculateAllowedMaxLemmaFrequencyTotal(
        currentLowestFrequencyValue, lemmasWithMaxFrequency.size());
    lemmasWithMaxFrequency.entrySet()
        .removeIf(entry -> entry.getValue() > currentAllowedMaxLemmaFrequencyTotal);
    lemmas = new ArrayList<>();
    for(Entry<String, Integer> entry : lemmasWithMaxFrequency.entrySet()) {
      lemmas.add(entry.getKey());
    }
    lemmasWithMaxFrequency.entrySet().removeIf(entry -> entry == currentLowestFrequencyValue.get());
    foundPageIds = new ArrayList<>(fillInitialPages(site, currentLowestFrequencyValue));
    if (!lemmasWithMaxFrequency.isEmpty()) {
      fillRemainingPages(site);
    }
    if (foundPageIds.isEmpty()) {
      return new SearchResponseSucceeded(true, 0, new ArrayList<>());
    }
    pageIdsWithMaxRel = new HashMap<>(collectIdsAndRankSum(foundPageIds));
    pageIdsWithRelRel = new HashMap<>(calculatePageIdsWithRelRel(pageIdsWithMaxRel, calculateMaxRel(pageIdsWithMaxRel)));
    data = new ArrayList<>(finishSearch(calculateOffset(offset)));
    return new SearchResponseSucceeded(true, pageIdsWithRelRel.size(), data);
  }
  
  private ArrayList<SearchResponseItem> finishSearch(int offset){
    Map<Integer, Double> sortedMap = sortMapByValueDescending(pageIdsWithRelRel);
    List<ModelPage> allPagesList = pagesRepository.findAllPagesByPageIds(foundPageIds);
    HashMap<Integer, Double> localPageIdsWithRelRel = sortedMap.entrySet().stream()
        .skip(offset)
        .limit(10)
        .collect(
        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new)
        );
    ArrayList<SearchResponseItem> dataLocal = new ArrayList<>();
      while(!localPageIdsWithRelRel.isEmpty()){
        Optional<Entry<Integer, Double>> localCurrentLowestRelRelPage = localPageIdsWithRelRel.entrySet()
            .stream()
            .min((Entry<Integer, Double> e1, Entry<Integer, Double> e2) -> e1.getValue()
            .compareTo(e2.getValue())
             );
        localPageIdsWithRelRel.entrySet()
            .removeIf(entry -> entry == localCurrentLowestRelRelPage.get());
        Optional<ModelPage> modelPage = allPagesList
            .stream()
            .filter((modelPage1 -> modelPage1.getId()==localCurrentLowestRelRelPage.get().getKey()))
            .findFirst();
        Optional<ModelSite> modelSite = Optional.ofNullable(modelPage.get().getModelSite());
        dataLocal.add(new SearchResponseItem(
            modelSite.get().getUrl(),
            modelSite.get().getName(),
            modelPage.get().getPath(),
            Jsoup.parse(modelPage.get().getContent()).title(),
            lemmaToSnippet(lemmas, Jsoup.parse(modelPage.get().getContent()).text()),
            localCurrentLowestRelRelPage.get().getValue()
        ));
      }
    dataLocal.sort(new Comparator<SearchResponseItem>() {
      @Override
      public int compare(SearchResponseItem o1, SearchResponseItem o2) {
        return o2.getRelevance().compareTo(o1.getRelevance());
      }
    });
      return dataLocal;
  }

  private ArrayList<Integer> fillInitialPages(String site,
      Optional<Entry<String, Integer>> localCurrentLowestFrequencyValue) {
//    lemmas.add(localCurrentLowestFrequencyValue.get().getKey());
    if (site == null) {
      lemmaIds = lemmaRepository.findLemmaIdByLemma(
          localCurrentLowestFrequencyValue.get().getKey());
      return new ArrayList<>(getPageIds(lemmaIds));
    } else {
      int localSiteId = sitesRepository.findIdByUrl(site);
      lemmaIds = lemmaRepository.findLemmaIdByLemmaAndSiteId(
          localCurrentLowestFrequencyValue.get().getKey(), localSiteId);
      return new ArrayList<>(getPageIds(lemmaIds));
    }
  }

  private void fillRemainingPages(String site) {
    while (!lemmasWithMaxFrequency.isEmpty()) {
      Optional<Entry<String, Integer>> localCurrentLowestFrequencyValue = calculateLowestFrequency(
          lemmasWithMaxFrequency);
//      lemmas.add(localCurrentLowestFrequencyValue.get().getKey());
      lemmasWithMaxFrequency.entrySet()
          .removeIf(entry -> entry == localCurrentLowestFrequencyValue.get());
      ArrayList<Integer> finalPageIds = fillInitialPages(site, localCurrentLowestFrequencyValue);
      for(int z = 0;foundPageIds.size()> z;z++){
//      for (int i = 0; finalPageIds.size() > i; i++) {
          if(!finalPageIds.contains(foundPageIds.get(z))){
            foundPageIds.remove(z);
          }
//        }
      }
    }
  }

  public LinkedHashMap<Integer, Double> sortMapByValueDescending(HashMap<Integer, Double> map) {
    return map.entrySet()
        .stream()
        .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
        .collect(
            Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
  }
}

