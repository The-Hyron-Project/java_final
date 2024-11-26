package searchengine.processors;

import java.io.IOException;
import java.util.ArrayList;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.services.EnglishServiceWords;
import searchengine.services.RussianServiceWords;

public class WordProcessor {

  public static boolean isServiceWord(String wordToCheck){
    if(wordToCheck.matches("[а-яА-Я]+") && !wordToCheck.isBlank() ){
      return isRussianServiceWord(wordToCheck);
    }else if(wordToCheck.matches("[a-zA-Z]+") && !wordToCheck.isBlank() ){
      return isEnglishServiceWord(wordToCheck);
    } else if (wordToCheck.matches("[0-9]+") && !wordToCheck.isBlank()) {
      return true;
    }else{
      return true;
    }
  }

  private static boolean isEnglishServiceWord(String englishWordToCheck){
    return !simpleEnglishMorphInfo(englishWordToCheck).contains(EnglishServiceWords.INT.toString())
        && !simpleEnglishMorphInfo(englishWordToCheck).contains(EnglishServiceWords.ARTICLE.toString())
        && !simpleEnglishMorphInfo(englishWordToCheck).contains(EnglishServiceWords.CONJ.toString())
        && !simpleEnglishMorphInfo(englishWordToCheck).contains(EnglishServiceWords.PREP.toString());
  }

  private static boolean isRussianServiceWord(String russianWordToCheck){
    return !simpleRussianMorphInfo(russianWordToCheck).contains(RussianServiceWords.СОЮЗ.toString())
        && !simpleRussianMorphInfo(russianWordToCheck).contains(RussianServiceWords.МЕЖД.toString())
        && !simpleRussianMorphInfo(russianWordToCheck).contains(RussianServiceWords.ЧАСТ.toString())
        && !simpleRussianMorphInfo(russianWordToCheck).contains(RussianServiceWords.ПРЕДЛ.toString());
  }

  private static String simpleRussianMorphInfo(String wordToCheck){
    try {
      LuceneMorphology luceneMorph = new RussianLuceneMorphology();
      String morphInfoFull = luceneMorph.getMorphInfo(wordToCheck.toLowerCase()).toString();
      String[] morphInfo = morphInfoFull.replace("[","").replace("]","").split(" ");
      return morphInfo[1];
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String simpleEnglishMorphInfo(String wordToCheck) {
    try {
      LuceneMorphology luceneMorph = new EnglishLuceneMorphology();
      String morphInfoFull = luceneMorph.getMorphInfo(wordToCheck.toLowerCase()).toString();
      String[] morphInfo = morphInfoFull.replace("[", "").replace("]", "").split(" ");
      return morphInfo[1];
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
      try {
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();
        return luceneMorph.getNormalForms(russianWordToGetForm.toLowerCase()).get(0).toLowerCase();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

  private static String getDefaultEnglishForm(String englishWordToGetForm){
    try {
      LuceneMorphology luceneMorph = new EnglishLuceneMorphology();
      return luceneMorph.getNormalForms(englishWordToGetForm.toLowerCase()).get(0).toLowerCase();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ArrayList<String> arrayToSentence(String lemma, String[] words){
    ArrayList<String> snippetToReturn = new ArrayList<>();
    for (int i = 0; i < words.length; i++){
      if(words[i].matches("\\D*") && !words[i].isBlank()){
        String[] words2 = words[i].split("\\.\\s+|\\,*\\s+|\\.\\s*|-+|'|:|\"|\\?|«|»");
        for (int y = 0; y < words2.length; y++){
          if(words2[y].matches("[а-яА-ЯЁё]+") && !words[y].isBlank()) {
              if(getDefaultRussianForm(words2[y]).contains(lemma)) {
                snippetToReturn.add(createSentence(words, i));
              }
          }else if(words2[y].matches("[a-zA-Z]+") && !words[y].isBlank()){
            if(getDefaultEnglishForm(words2[y]).contains(lemma)) {
              snippetToReturn.add(createSentence(words, i));
            }
          }
        }
      }else if(!words[i].isBlank() && words[i].matches("[0-9]+") && words[i].matches(lemma)){
        snippetToReturn.add(createSentence(words, i));
      }
    }
    return snippetToReturn;
  }

  private static String createSentence(String[] words, int i ){
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("...");
    for(int z = 15;1<=z;z--){
      if(!(i-z<0)){
        stringBuilder.append(words[i-z]);
        stringBuilder.append(" ");
      }
    }
    stringBuilder.append("<b>"+words[i]+"</b>");
    for(int z = 1;15>=z;z++) {
      if (!(i + z > words.length-1)) {
        stringBuilder.append(" ");
        stringBuilder.append(words[i + z]);
      }
    }
    stringBuilder.append("...");
    return stringBuilder.toString();
  }
}
