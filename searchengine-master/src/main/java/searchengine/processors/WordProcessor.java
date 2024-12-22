package searchengine.processors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.services.EnglishServiceWords;
import searchengine.services.RussianServiceWords;

public class WordProcessor {
  static LuceneMorphology luceneMorphRu;
  static LuceneMorphology luceneMorphEng;
  private static ArrayList <String> lemmas;
  private static ArrayList<Integer> indexesToIgnore;
  private static ArrayList<Integer> indexes;
  private static boolean isSnippetFound = false;
  private static boolean isDefaultStringGot = false;
  private static String defaultString;
  private static int hasRightNeighbourCount;

  static {
    try {
      luceneMorphEng = new EnglishLuceneMorphology();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try {
      luceneMorphRu = new RussianLuceneMorphology();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean isNotServiceWord(String wordToCheck){
    if(wordToCheck.matches("[а-яА-Я]+") && !wordToCheck.isBlank() ){
      return isNotRussianServiceWord(wordToCheck);
    }else if(wordToCheck.matches("[a-zA-Z]+") && !wordToCheck.isBlank() ){
      return isNotEnglishServiceWord(wordToCheck);
    } else if (wordToCheck.matches("[0-9]+") && !wordToCheck.isBlank()) {
      return true;
    }else{
      return true;
    }
  }

  private static boolean isNotEnglishServiceWord(String englishWordToCheck){
    return !simpleEnglishMorphInfo(englishWordToCheck).contains(EnglishServiceWords.INT.toString())
        && !simpleEnglishMorphInfo(englishWordToCheck).contains(EnglishServiceWords.ARTICLE.toString())
        && !simpleEnglishMorphInfo(englishWordToCheck).contains(EnglishServiceWords.CONJ.toString())
        && !simpleEnglishMorphInfo(englishWordToCheck).contains(EnglishServiceWords.PREP.toString());
  }

  private static boolean isNotRussianServiceWord(String russianWordToCheck){
    return !simpleRussianMorphInfo(russianWordToCheck).contains(RussianServiceWords.СОЮЗ.toString())
        && !simpleRussianMorphInfo(russianWordToCheck).contains(RussianServiceWords.МЕЖД.toString())
        && !simpleRussianMorphInfo(russianWordToCheck).contains(RussianServiceWords.ЧАСТ.toString())
        && !simpleRussianMorphInfo(russianWordToCheck).contains(RussianServiceWords.ПРЕДЛ.toString());
  }

  private static String simpleRussianMorphInfo(String wordToCheck){
      String morphInfoFull = luceneMorphRu.getMorphInfo(wordToCheck.toLowerCase()).toString();
      String[] morphInfo = morphInfoFull.replace("[","").replace("]","").split(" ");
      return morphInfo[1];
  }

  private static String simpleEnglishMorphInfo(String wordToCheck) {
      String morphInfoFull = luceneMorphEng.getMorphInfo(wordToCheck.toLowerCase()).toString();
      String[] morphInfo = morphInfoFull.replace("[", "").replace("]", "").split(" ");
      return morphInfo[1];
  }

  public static String getDefaultForm(String wordToGetForm){
    if(wordToGetForm.matches("[а-яА-ЯЁё]+") && !wordToGetForm.isBlank() ){
      return getDefaultRussianForm(wordToGetForm);
    }else if(wordToGetForm.matches("[a-zA-Z]+") && !wordToGetForm.isBlank() ){
      return getDefaultEnglishForm(wordToGetForm);
    } else if (wordToGetForm.matches("[0-9]+") && !wordToGetForm.isBlank()){
      return wordToGetForm;
    }
    return "";
  }

  private static String getDefaultRussianForm(String russianWordToGetForm){
        return luceneMorphRu.getNormalForms(russianWordToGetForm.toLowerCase()).get(0).toLowerCase();
    }

  private static String getDefaultEnglishForm(String englishWordToGetForm){
      return luceneMorphEng.getNormalForms(englishWordToGetForm.toLowerCase()).get(0).toLowerCase();
  }

  public static String arrayToSentence(ArrayList<String> lemmasReceived, String text){
    lemmas = lemmasReceived;
    indexes = new ArrayList<>();
    indexesToIgnore = new ArrayList<>();
    String[] words = text.split("(?<=\\.\\s)+|(?=\\.\\s)+|(?<=\\,)*(?<=\\s)+|(?=\\,)*(?=\\s)+|(?<=-)+|(?=-)+|(?<=')|(?=')|(?<=:)|(?=:)|(?<=\")|(?=\")|(?<=\\?)|(?=\\?)|(?<=«)|(?=«)|(?<=»)|(?=»)|(?<=,)|(?=,)");
    isSnippetFound=false;
    isDefaultStringGot = false;
    defaultString = "";
    ArrayList<String> snippetToReturn = new ArrayList<>();
    for (int i = 0; i < words.length; i++){
      if(isSnippetFound==true){
        indexes  = new ArrayList<>();
        break;
      }
      if(!indexesToIgnore.contains(i)){
        hasRightNeighbourCount = 0;
        snippetToReturn.addAll(iterateThroughInitiallySeparatedWords(words, i));
      }
    }
    StringBuilder stringBuilder = new StringBuilder();
    if(isSnippetFound==false && isDefaultStringGot==true){
      snippetToReturn.add(defaultString);
    }
    for(int u=0;snippetToReturn.size()>u;u++){
      stringBuilder.append(snippetToReturn.get(u));
    }
    return stringBuilder.toString();
  }

  private static ArrayList<String> iterateThroughInitiallySeparatedWords(String[] words, int i){
    ArrayList<String> localsSnippetToReturn = new ArrayList<>();
    hasRightNeighbourCount = 0;
    if(words[i].matches("\\D*") && !words[i].isBlank()) {
      if (words[i].matches("[а-яА-ЯЁё]+") && !words[i].isBlank()) {
        if (lemmas.contains(getDefaultRussianForm(words[i]))) {
          boolean hasRightNeighbour = hasRightNeighbour(i, words, 0);
          if (isSnippetFound == false && !hasRightNeighbour) {
            if(isDefaultStringGot == false && isSnippetFound == false){
              defaultString = createSentence(words, i);
              isDefaultStringGot = true;
            }
          } else if (isSnippetFound == false &&  hasRightNeighbour==true) {
            indexes.addAll(getRightNeighbour(i, 0, words));
            indexes.add(i);
            indexesToIgnore.addAll(indexes);
            localsSnippetToReturn.add(createGiantSentence(words, indexes));
            isSnippetFound = true;
          }
        }
      } else if (words[i].matches("[a-zA-Z]+") && !words[i].isBlank()) {
        if (lemmas.contains(getDefaultEnglishForm(words[i]))) {
          indexes.add(i);
          boolean hasRightNeighbour = hasRightNeighbour(i, words, 0);
          if (!hasRightNeighbour) {
            if(isDefaultStringGot == false && isSnippetFound == false){
              defaultString = createSentence(words, i);
              isDefaultStringGot = true;
            }
          } else if (isSnippetFound == false && hasRightNeighbour) {
            indexes.addAll(getRightNeighbour(i, 0, words));
            indexesToIgnore.addAll(indexes);
            localsSnippetToReturn.add(createGiantSentence(words, indexes));
            isSnippetFound = true;
          }
        }
      }
    }else if(!words[i].isBlank() && words[i].matches("[0-9]+") && lemmas.contains(words[i])) {
      boolean hasRightNeighbour = hasRightNeighbour(i, words, 0);
      if (!hasRightNeighbour) {
        if(isDefaultStringGot == false && isSnippetFound == false){
          defaultString = createSentence(words, i);
          isDefaultStringGot = true;
        }
      }
      else if (isSnippetFound == false &&  hasRightNeighbour) {
        indexes.addAll(getRightNeighbour(i, 0, words));
        indexes.add(i);
        indexesToIgnore.addAll(indexes);
        localsSnippetToReturn.add(createGiantSentence(words, indexes));
        isSnippetFound = true;
      }
    }
    return localsSnippetToReturn;
  }

  private static boolean hasRightNeighbour(int i, String[] words, int counter) {
    counter++;
    Boolean result = false;
      if (i+counter < words.length && words[i+counter] != null) {
        while (i+counter < words.length
            && words[i+counter] != null
            &&
            !(words[i+counter].matches("[а-яА-ЯЁё]+")
                || words[i+counter].matches("[a-zA-Z]+")
                || words[i+counter].matches("[0-9]+"))){
          counter++;
        }
        if (words[i+counter].matches("\\D*") && !words[i].isBlank()) {
            if (words[i+counter].matches("[а-яА-ЯЁё]+") && !words[i+counter].isBlank()
                && isNotRussianServiceWord(getDefaultRussianForm(words[i+counter]))
                && lemmas.contains(getDefaultRussianForm(words[i+counter]))) {
              hasRightNeighbourCount++;
              if (hasRightNeighbourCount < lemmas.size()-1) {
                return hasRightNeighbour(i, words, counter);
              } else if (hasRightNeighbourCount == lemmas.size()-1) {
                result = true;
              } else {
                result = false;
              }
            } else if (words[i+counter].matches("[a-zA-Z]+") && !words[i+counter].isBlank()
                && isNotEnglishServiceWord(getDefaultEnglishForm(words[i+counter]))
                && lemmas.contains(getDefaultEnglishForm(words[i+counter]))) {
              hasRightNeighbourCount++;
              if (hasRightNeighbourCount < lemmas.size()-1) {

                return hasRightNeighbour(i, words, counter);
              } else if (hasRightNeighbourCount == lemmas.size()-1) {
                result = true;
              } else {
                result = false;
              }
          }
        } else if (!words[i+counter].isBlank() && words[i+counter].matches("[0-9]+") && lemmas.contains(words[i+counter])) {
          hasRightNeighbourCount++;
          if (hasRightNeighbourCount < lemmas.size()-1) {
            return hasRightNeighbour(i, words, hasRightNeighbourCount);
          } else if (hasRightNeighbourCount == lemmas.size()-1) {
            result = true;
          } else {
            result = false;
          }
        }
      }
    return result;
  }

  private static ArrayList<Integer> getRightNeighbour(int i, int counter, String[] words) {
    ArrayList<Integer> result = new ArrayList();
    int getRightNeighbourCount = 0;
    counter++;
    while(getRightNeighbourCount<hasRightNeighbourCount){
      while (!(words[i+counter].matches("[а-яА-ЯЁё]+")
              || words[i+counter].matches("[a-zA-Z]+")
              || words[i+counter].matches("[0-9]+"))){
        counter++;
      }
      if (words[i+counter].matches("[а-яА-ЯЁё]+") && lemmas.contains(getDefaultRussianForm(words[i+counter]))) {
        result.add(i+counter);
        getRightNeighbourCount++;
        counter++;
      } else if (words[i+counter].matches("[a-zA-Z]+") && lemmas.contains(getDefaultRussianForm(words[i+counter]))) {
        result.add(i+counter);
        getRightNeighbourCount++;
        counter++;
      } else if (words[i+counter].matches("[0-9]+") && lemmas.contains(words[i+counter])) {
        result.add(i+counter);
        getRightNeighbourCount++;
        counter++;
      }
    }
    return result;
  }

  private static String createSentence(String[] words, int i ){
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("...");
    Boolean isInserted = false;
    for(int z = 15;1<=z;z--){
      if(!(i-z<0)) {
        isInserted = false;
          if ((words[i - z].matches("[а-яА-ЯЁё]+") && lemmas.contains(getDefaultRussianForm(words[i - z])))
              ||
              (words[i - z].matches("[a-zA-Z]+") && lemmas.contains(getDefaultEnglishForm(words[i - z])))
              ||
              (words[i - z].matches("[0-9]+") && lemmas.contains(words[i - z])))
          {
            stringBuilder.append("<b>").append(words[i - z]).append("</b>");
            isInserted = true;
//          }
        }
        if(isInserted==false){
          stringBuilder.append(words[i - z]);
        }
//        stringBuilder.append(" ");
      }else {

        }
      }
    stringBuilder.append("<b>"+words[i]+"</b>");
    for(int z = 1;15>=z;z++) {
      if (!(i + z > words.length-1)) {
        isInserted = false;
//        stringBuilder.append(" ");
        if(words[i + z].matches("\\D*") && !words[i].isBlank()) {
//          String[] words2 = words[i + z].split("\\.\\s+|\\,\\s+|\\.\\s*|-+|'|:|\"|\\?|«|»|,");
//        for (int y = 0; y < words2.length; y++) {
          if ((words[i + z].matches("[а-яА-ЯЁё]+") && lemmas.contains(getDefaultRussianForm(words[i + z])))
              ||
              (words[i + z].matches("[a-zA-Z]+") && lemmas.contains(getDefaultEnglishForm(words[i + z])))
              ||
              ((words[i + z].matches("[0-9]+") && lemmas.contains((words[i + z])))))
          {
            stringBuilder.append("<b>").append(words[i + z]).append("</b>");
            isInserted = true;
          }
//        }
        }else if (!words[i + z].isBlank() && words[i + z].matches("[0-9]+") && lemmas.contains(words[i + z])) {
          stringBuilder.append("<b>").append(words[i + z]).append("</b>");
          isInserted = true;
        }
        if(isInserted==false){
          stringBuilder.append(words[i + z]);
        }
      }
    }
    stringBuilder.append("...");
    return stringBuilder.toString();
  }

  private static String createGiantSentence(String[] words, ArrayList<Integer> wordIds){
    int min = wordIds.stream().mapToInt(v -> v)
        .min().getAsInt();
    int max = wordIds.stream().mapToInt(v -> v)
        .max().getAsInt();
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("...");
    for(int z = 14;0<z;z--){
      if(!((min-z) <0)){
      if(wordIds.contains(min-z)){
        stringBuilder.append("<b>").append(words[min-z]).append("</b>");
      }else{
        stringBuilder.append(words[min-z]);
      }
      }
    }
    for(int z = min;z!=(max+1);z++){
      if(wordIds.contains(z)){
        stringBuilder.append("<b>").append(words[z]).append("</b>");
      }else{
        stringBuilder.append(words[z]);
      }
    }

    for(int z = 1;14>=z;z++) {
      if (!(max+z > words.length-1)) {
        if(wordIds.contains(max + z)){
          stringBuilder.append("<b>").append(words[max + z]).append("</b>");
        }else{
          stringBuilder.append(words[max + z]);
        }
      }else{
        break;
      }
    }
    stringBuilder.append("...");
    return stringBuilder.toString();
  }
}
