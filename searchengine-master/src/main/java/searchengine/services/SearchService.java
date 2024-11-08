package searchengine.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.RequestResponceFailed;
import searchengine.dto.statistics.RequestResponceSucceeded;
import searchengine.dto.statistics.RequestResponse;
import searchengine.dto.statistics.SearchResponseItem;
import searchengine.dto.statistics.SearchResponseSucceeded;
import searchengine.model.ModelPage;
import searchengine.model.ModelSite;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

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
  HashMap<String, Integer> lemmasWithMaxFrequency;
  Optional<Entry<String, Integer>> currentLowestFrequencyValue;
  HashMap<String, ArrayList<ModelPage>> lemmasWithPages;
  ArrayList<ModelPage> pages;
  HashMap<Integer, Integer> pageIdsWithMaxRel;
  HashMap<Integer, Double> pageIdsWithRelRel;
  HashMap<String, HashMap<String, ArrayList<String>>> lemmasWithPathsAndSnippets;
  Document doc2 = null;
  Document doc3 = null;
  RequestResponceFailed requestResponseFailed;
  SearchResponseSucceeded searchResponseSucceeded;
  ArrayList<SearchResponseItem> data;
  String siteToReturn="";
  String siteNameToReturn="";
  String uriToReturn="";
  String titleToReturn="";
  String snippetToReturn="";
  Double relevanceToReturn=0d;

  public HashMap<String, Integer> getEachLemmaMaxFrequency(ArrayList<String> separatedWords){
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

  public ArrayList<String> lemmaToSnippet(String lemma, String text){
    ArrayList<String> snippetLocal = new ArrayList<>();
    String[] words = text.split(" ");
    StringBuilder stringBuilder;

    ArrayList<Integer> wordsIndexes = new ArrayList<>();
    try{
      LuceneMorphology luceneMorph = new RussianLuceneMorphology();
      for (int i = 0; i < words.length; i++){
        if(words[i].matches("\\D*") && !words[i].isBlank() ){
          String[] words2 = words[i].split("\\.\\s+|\\,*\\s+|\\.\\s*|-+|'|:|\"|\\?|«|»");
          for (int y = 0; y < words2.length; y++){
            if(words2[y].matches("\\D*") && !words[y].isBlank() ) {
              try{
                if(luceneMorph.getMorphInfo(words2[y].toLowerCase()).toString().contains(lemma)) {
                  stringBuilder = new StringBuilder();
                  stringBuilder.append("...");
                  for(int z = 10;1<=z;z--){
                    if(!(i-z<0)){
                      stringBuilder.append(words[i-z]);
                      stringBuilder.append(" ");

                    }
                  }
                  int s = words.length;
                  stringBuilder.append("<b>"+words[i]+"</b>");
                  for(int z = 1;10>=z;z++) {
                    if (!(i + z > words.length-1)) {
                      int ss = i + z;
                      stringBuilder.append(" ");
                      stringBuilder.append(words[i + z]);
                    }
                  }
                  stringBuilder.append("...");
                  snippetLocal.add(stringBuilder.toString());
                }
              }catch (Exception e) {
//            System.out.println(words[i] + " не подошло");
//            System.out.println(luceneMorph.getNormalForms(words[i].toLowerCase()));
//            brokenWords.add(words[i]);
              }
            }
          }
        }
      }
    }catch (Exception e) {
    }
    snippetLocal.size();
    return snippetLocal;
  }

  private <E extends Comparable<E>> int compare(E e1, E e2) {
    return e1.compareTo(e2);
  }

  public RequestResponse startSearching(String sentence, int offset, int limit, String site){
    if(sentence==null || sentence.isEmpty()){
      return requestResponseFailed = new RequestResponceFailed(false, "Задан пустой поисковый запрос");
    }else{
        separatedWords = sentenceToWords(sentence);
        lemmasWithMaxFrequency = getEachLemmaMaxFrequency(separatedWords);
        if(lemmasWithMaxFrequency.isEmpty()){
          return searchResponseSucceeded = new SearchResponseSucceeded(true, 0,
              new ArrayList<>());
        }
        pageIdsWithMaxRel = new HashMap<>();
        pageIdsWithRelRel = new HashMap<>();
        lemmasWithPathsAndSnippets = new HashMap<>();
        if(!lemmasWithMaxFrequency.isEmpty()){
          currentLowestFrequencyValue = lemmasWithMaxFrequency.entrySet()
              .stream()
              .min((Entry<String, Integer> e1, Entry<String, Integer> e2) -> e1.getValue()
                  .compareTo(e2.getValue())
              );
        }
        if(currentLowestFrequencyValue!=null && currentLowestFrequencyValue.isPresent()){
          currentAllowedMaxLemmaFrequencyTotal = (double) (currentLowestFrequencyValue.get().getValue()*lemmasWithMaxFrequency.size());
        }
        lemmasWithPages = new HashMap<>();
        int pagesArrayReduction = 0;
      int pagesCounter = 0;
      for(int i = 0; currentAllowedMaxLemmaFrequencyTotal>=i;i++){
          for(Map.Entry<String, Integer> entry : lemmasWithMaxFrequency.entrySet()) {
            if(entry.getValue() == i){
              List<Integer> lemmaIds = lemmaRepository.findLemmaIdByLemma(entry.getKey());
              ArrayList<Integer> pagesIds = new ArrayList<>();
              for(int z = 0;lemmaIds.size()>z;z++){
                List<Integer> pageIdsNoRel = indexRepository.findPageIdByLemmaId(lemmaIds.get(z));
                pagesIds.addAll(pageIdsNoRel);
                for(int p=0;pageIdsNoRel.size()>p;p++){
                  List<Integer> indexRanks = indexRepository.findAllRanksByPageId(pageIdsNoRel.get(p));
                  int rankSum = indexRanks.stream().mapToInt(Integer::intValue).sum();
                  pageIdsWithMaxRel.put(pageIdsNoRel.get(p), rankSum);
                }
              }
              pages = new ArrayList<>();
              int pagesCounterToIterate;
              if(pagesCounter!=0 && pagesIds.size()>pagesCounter){
                pagesCounterToIterate=pagesIds.size()-pagesArrayReduction;
              }else{
                pagesCounterToIterate=pagesIds.size();
              }
              for(int z = 0;pagesCounterToIterate>z;z++){
                ModelPage modelPageToCheck = pagesRepository.findById(pagesIds.get(z)).get();
                if(site!=null && !(site.isEmpty())){
                  if(Objects.equals(modelPageToCheck.getModelSite().getUrl(), site)){
                    pages.add(modelPageToCheck);
                  }
                }else{
                  pages.add(modelPageToCheck);
                }
              }
              lemmasWithPages.put(entry.getKey(), pages);
              if(pagesCounter-1<pages.size()){
                pagesCounter--;
              }else{
                pagesCounter=pages.size();
              }
              pagesArrayReduction++;
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

        for(Entry<String, ArrayList<ModelPage>> entry : lemmasWithPages.entrySet()){
          ArrayList<String> textArray;
          HashMap<String, ArrayList<String>> dataToInsert  = new HashMap<>();;
          String path = "";
          for(int i = 0;entry.getValue().size()>i;i++){
            textArray = new ArrayList<>();
            ModelSite modelSite = entry.getValue().get(i).getModelSite();
            doc2 = Jsoup.parse(entry.getValue().get(i).getContent());
            path=modelSite.getUrl()+entry.getValue().get(i).getPath();
            textArray.addAll(lemmaToSnippet(entry.getKey(), doc2.body().text()));
            dataToInsert.put(path, textArray);
          }
          lemmasWithPathsAndSnippets.put(entry.getKey(), dataToInsert);
        }
      data = new ArrayList<>();
      lemmasWithMaxFrequency.entrySet().removeIf(entry -> entry.getValue()>currentAllowedMaxLemmaFrequencyTotal);
      for(int i = 0; currentAllowedMaxLemmaFrequencyTotal>=i;i++){
        for(Map.Entry<String, Integer> entry : lemmasWithMaxFrequency.entrySet()) {
          if(entry.getValue() == i){
            for(Map.Entry<String, ArrayList<ModelPage>> entry2 : lemmasWithPages.entrySet()) {
              if(Objects.equals(entry.getKey(), entry2.getKey())){
                for(int t=0; entry2.getValue().size()>t;t++){
                  for(Entry<String, HashMap<String, ArrayList<String>>> entry3 : lemmasWithPathsAndSnippets.entrySet()) {
                    if(Objects.equals(entry3.getKey(), entry.getKey())){
                      for(Entry<String, ArrayList<String>> entry4 : entry3.getValue().entrySet()) {
                        if(Objects.equals(entry4.getKey(), entry2.getValue().get(t).getModelSite().getUrl() + entry2.getValue().get(t).getPath())){
                          for(int u = 0;entry4.getValue().size()>u;u++){

                            ModelSite modelSite1 = entry2.getValue().get(t).getModelSite();
                            siteToReturn=modelSite1.getUrl();
                            uriToReturn=entry2.getValue().get(t).getPath();
                            siteNameToReturn=modelSite1.getName();
                            doc3=Jsoup.parse(entry2.getValue().get(t).getContent());
                            titleToReturn=doc3.title();
                            relevanceToReturn = pageIdsWithRelRel.get(entry2.getValue().get(t).getId());
                            snippetToReturn=entry4.getValue().get(u);

                            SearchResponseItem searchResponseItem = new SearchResponseItem(siteToReturn, siteNameToReturn,
                                uriToReturn, titleToReturn, snippetToReturn, relevanceToReturn);
                            data.add(searchResponseItem);

                          }
                        }
                      }
                    }
                  }
                }
              }
            }
              }
            }
          }
        if(offset>=data.size()){
          offset=0;
        }
        if(limit+offset>data.size()){
          limit=data.size();
        }else{
          limit=limit+offset;
        }

      return searchResponseSucceeded = new SearchResponseSucceeded(true, data.size(),
          new ArrayList<>(data.subList(offset, limit)));
    }
  }
}

