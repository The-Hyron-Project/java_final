package searchengine.processors;

import java.io.IOException;
import java.util.ArrayList;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.services.EnglishServiceWords;
import searchengine.services.RussianServiceWords;

public class WordProcessor {
  static LuceneMorphology luceneMorphRu;
  static LuceneMorphology luceneMorphEng;
  private static ArrayList <String> lemmas;
  private static ArrayList<Integer> indexes;
  private static boolean isSnippetFound = false;
  private static boolean isDefaultStringGot = false;
  private static String defaultString;
  private static int hasRightNeighbourCount;
  private static String finalLemma;

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

  private static void prepareToWork(){
    indexes = new ArrayList<>();
    isSnippetFound=false;
    isDefaultStringGot = false;
    defaultString = "";
    finalLemma = "";
  }

  public static String arrayToSentence(ArrayList<String> lemmasReceived, String text){
    lemmas = lemmasReceived;
    prepareToWork();
    String[] words = text.split("(?<=\\.\\s)+|(?=\\.\\s)+|(?<=\\,)*(?<=\\s)+|(?=\\,)*(?=\\s)+|(?<=-)+|(?=-)+|(?<=')|(?=')|(?<=:)|(?=:)|(?<=\")|(?=\")|(?<=\\?)|(?=\\?)|(?<=«)|(?=«)|(?<=»)|(?=»)|(?<=,)|(?=,)");
    for (int i = 0; i < words.length; i++){
      if(isSnippetFound==true){
        indexes  = new ArrayList<>();
        break;
      }
      hasRightNeighbourCount = 0;
      finalLemma = iterateThroughSeparatedWords(words, i);
    }
    if(isSnippetFound==false && isDefaultStringGot==true){
      return defaultString;
    }
    return finalLemma;
  }

  private static String iterateThroughSeparatedWords(String[] words, int wordIndex){
    String localsSnippetToReturn = "";
    hasRightNeighbourCount = 0;
    if (words[wordIndex].matches("[а-яА-ЯЁё]+") && !words[wordIndex].isBlank()) {
      if (lemmas.contains(getDefaultRussianForm(words[wordIndex].replace('Ё','Е').replace('ё','е')))) {
        return processRussianWord(words, wordIndex);
      }
    } else if (words[wordIndex].matches("[a-zA-Z]+") && !words[wordIndex].isBlank()) {
      if (lemmas.contains(getDefaultEnglishForm(words[wordIndex]))) {
        return processEnglishWord(words, wordIndex);
      }
    }else if(!words[wordIndex].isBlank() && words[wordIndex].matches("[0-9]+") && lemmas.contains(words[wordIndex])) {
      return processDigit(words, wordIndex);
    }
    return localsSnippetToReturn;
  }

  private static String processRussianWord(String[] words, int wordIndex){
    boolean hasRightNeighbour = hasRightNeighbour(wordIndex, words, 0);
    if (isSnippetFound == false && hasRightNeighbour==false && isDefaultStringGot == false) {
      createSentenceNoNeighbour(words, wordIndex);
    } else if (isSnippetFound == false && hasRightNeighbour==true) {
      return createSentenceWithNeighbour(words, wordIndex);
    }
    return "";
  }

  private static String processEnglishWord(String[] words, int wordIndex){
    indexes.add(wordIndex);
    boolean hasRightNeighbour = hasRightNeighbour(wordIndex, words, 0);
    if (isSnippetFound == false && hasRightNeighbour==false && isDefaultStringGot == false) {
      createSentenceNoNeighbour(words, wordIndex);
    } else if (isSnippetFound == false && hasRightNeighbour==true) {
      return createSentenceWithNeighbour(words, wordIndex);
    }
    return "";
  }

  private static String processDigit(String[] words, int wordIndex){
    boolean hasRightNeighbour = hasRightNeighbour(wordIndex, words, 0);
    if (isSnippetFound == false && hasRightNeighbour==false && isDefaultStringGot == false) {
      createSentenceNoNeighbour(words, wordIndex);
    }
    else if (isSnippetFound == false && hasRightNeighbour==true) {
      return createSentenceWithNeighbour(words, wordIndex);
    }
    return "";
  }

  private static void createSentenceNoNeighbour(String[] words, int wordIndex){
    defaultString = createSentence(words, wordIndex);
    isDefaultStringGot = true;
  }

  private static String createSentenceWithNeighbour(String[] words, int wordIndex){
    indexes.addAll(getRightNeighbour(wordIndex, 0, words));
    indexes.add(wordIndex);
    isSnippetFound = true;
    return createGiantSentence(words, indexes);
  }

  private static boolean hasRightNeighbour(int wordCounter, String[] words, int counter) {
    counter++;
    Boolean result = false;
      if (wordCounter+counter < words.length && words[wordCounter+counter] != null) {
        while (wordCounter+counter < words.length && words[wordCounter+counter] != null && !(((words[wordCounter+counter].matches("[а-яА-ЯЁё]+") || words[wordCounter+counter].matches("[a-zA-Z]+")) && isNotServiceWord(words[wordCounter+counter])) || words[wordCounter+counter].matches("[0-9]+"))){
          counter++;
        }
        if (words[wordCounter+counter].matches("[а-яА-ЯЁё]+") && !words[wordCounter+counter].isBlank() && lemmas.contains(getDefaultRussianForm(words[wordCounter+counter]))) {
          return checkRussianNeighbour(wordCounter,words, counter);
        } else if (words[wordCounter+counter].matches("[a-zA-Z]+") && !words[wordCounter+counter].isBlank() && isNotEnglishServiceWord(getDefaultEnglishForm(words[wordCounter+counter])) && lemmas.contains(getDefaultEnglishForm(words[wordCounter+counter]))) {
          return checkEnglishNeighbour(wordCounter,words, counter);
        } else if (!words[wordCounter+counter].isBlank() && words[wordCounter+counter].matches("[0-9]+") && lemmas.contains(words[wordCounter+counter])) {
          return checkNumericNeighbour(wordCounter,words, counter);
        }
      }
    return result;
  }

  private static Boolean checkNumericNeighbour(int wordCounter, String[] words, int counter){
    hasRightNeighbourCount++;
    if (hasRightNeighbourCount < lemmas.size()-1) {
      return hasRightNeighbour(wordCounter, words, counter);
    } else if (hasRightNeighbourCount == lemmas.size()-1) {
      return true;
    } else {
      return false;
    }
  }

  private static Boolean checkRussianNeighbour(int wordCounter, String[] words, int counter){
    hasRightNeighbourCount++;
    if (hasRightNeighbourCount < lemmas.size()-1) {
      return hasRightNeighbour(wordCounter, words, counter);
    } else if (hasRightNeighbourCount == lemmas.size()-1) {
      return true;
    } else {
      return false;
    }
  }

  private static Boolean checkEnglishNeighbour(int wordCounter, String[] words, int counter){
    hasRightNeighbourCount++;
    if (hasRightNeighbourCount < lemmas.size()-1) {
      return hasRightNeighbour(wordCounter, words, counter);
    } else if (hasRightNeighbourCount == lemmas.size()-1) {
      return true;
    } else {
      return false;
    }
  }

  private static ArrayList<Integer> getRightNeighbour(int wordCounter, int counter, String[] words) {
    ArrayList<Integer> result = new ArrayList();
    int getRightNeighbourCount = 0;
    counter++;
    while(getRightNeighbourCount<hasRightNeighbourCount){
      while (!(((words[wordCounter+counter].matches("[а-яА-ЯЁё]+") || words[wordCounter+counter].matches("[a-zA-Z]+")) && isNotServiceWord(words[wordCounter+counter])) || words[wordCounter+counter].matches("[0-9]+"))){
        counter++;
      }
      if (words[wordCounter+counter].matches("[а-яА-ЯЁё]+") && lemmas.contains(getDefaultRussianForm(words[wordCounter+counter]))) {
        result.add(wordCounter+counter);
        getRightNeighbourCount++;
        counter++;
      } else if (words[wordCounter+counter].matches("[a-zA-Z]+") && lemmas.contains(getDefaultRussianForm(words[wordCounter+counter]))) {
        result.add(wordCounter+counter);
        getRightNeighbourCount++;
        counter++;
      } else if (words[wordCounter+counter].matches("[0-9]+") && lemmas.contains(words[wordCounter+counter])) {
        result.add(wordCounter+counter);
        getRightNeighbourCount++;
        counter++;
      }
    }
    return result;
  }

  private static String createSentence(String[] words, int wordCounter ){
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("...");
    for(int z = 15;1<=z;z--){
      if(!(wordCounter-z<0)) {
        stringBuilder.append(howToInsertByLemma(wordCounter - z, words));
      }
    }
    stringBuilder.append("<b>").append(words[wordCounter]).append("</b>");
    for(int z = 1;15>=z;z++) {
      if (!(wordCounter + z > words.length-1)) {
        stringBuilder.append(howToInsertByLemma(wordCounter + z, words));
      }
    }
    stringBuilder.append("...");
    return stringBuilder.toString();
  }

  private static String createGiantSentence(String[] words, ArrayList<Integer> wordIds){
    int min = wordIds.stream().mapToInt(v -> v).min().getAsInt();
    int max = wordIds.stream().mapToInt(v -> v).max().getAsInt();
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("...");
    for(int z = 14;0<z;z--){
      if(!((min-z) <0)){
        stringBuilder.append(howToInsertById(min-z, words, wordIds));
      }
    }
    for(int z = min;z!=(max+1);z++){
      stringBuilder.append(howToInsertById(z, words, wordIds));
    }
    for(int z = 1;14>=z;z++) {
      if (!(max+z > words.length-1)){
        stringBuilder.append(howToInsertById(max+z, words, wordIds));
      }else{
        break;
      }
    }
    stringBuilder.append("...");
    return stringBuilder.toString();
  }

  private static String howToInsertByLemma(int wordIndex, String[] words){
    if(words[wordIndex].matches("\\D*") && !words[wordIndex].isBlank()) {
      if ((words[wordIndex].matches("[а-яА-ЯЁё]+") && lemmas.contains(getDefaultRussianForm(words[wordIndex].replace('Ё','Е').replace('ё','е'))))
          ||
          (words[wordIndex].matches("[a-zA-Z]+") && lemmas.contains(getDefaultEnglishForm(words[wordIndex])))
          ||
          ((words[wordIndex].matches("[0-9]+") && lemmas.contains((words[wordIndex])))))
      {
        return "<b>"+words[wordIndex].replace('Ё','Е').replace('ё','е')+"</b>";
      }
    }else if (!words[wordIndex].isBlank() && words[wordIndex].matches("[0-9]+") && lemmas.contains(words[wordIndex])) {
      return words[wordIndex];
    }
    return words[wordIndex];
  }

  private static String howToInsertById(int wordIndex, String[] words, ArrayList<Integer> wordIds){
    if(wordIds.contains(wordIndex)){
      return "<b>"+words[wordIndex]+"</b>";
    }else{
      return words[wordIndex];
    }
  }
}
