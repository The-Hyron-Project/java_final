package searchengine.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.model.ModelPage;
import searchengine.model.ModelSite;
import searchengine.model.Status;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

//тест4
@AllArgsConstructor
@Service
public class IndexingService extends RecursiveAction {

  @Autowired
  public PagesRepository pagesRepository;
  @Autowired
  public SitesRepository sitesRepository;
  @Autowired
  public SitesList initialConSites;

  int level;
  static int numberOfLines = 50;
  static int depth = 0;
  String url = "";
  String name = "";
  String baseUrl = "";
  Document doc2 = null;
  Document doc3 = null;
  static ArrayList<String> uncheckedCheckerLinks;
  List<IndexingService> subTasks = new ArrayList<>();
  List<IndexingService> subFirstTasks = new ArrayList<>();;
  int siteId;
  Boolean isSuccessful = false;
  Connection.Response response = null;
  public Boolean isFirstRun = true;
  public static Boolean isIndexing = false;
  static List<String> siteNames = new ArrayList<>();
  public static ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
  public List<String> arguments;

  public IndexingService() {}

  @Builder
  public IndexingService(List<String> arguments, SitesRepository sitesRepository, PagesRepository pagesRepository) {
    this.url = arguments.get(0);
    this.baseUrl = arguments.get(1);
    this.siteId = Integer.parseInt(arguments.get(2));
    this.name=arguments.get(3);
    this.initialConSites=null;
    this.isFirstRun= Boolean.valueOf(arguments.get(5));
    this.level = Integer.parseInt(arguments.get(6));
    this.pagesRepository = pagesRepository;
    this.sitesRepository = sitesRepository;
  }

  public Boolean getIndexResult() {
    if(!isIndexing){
      isIndexing = true;
      startIndexing();
      compute();
      for(int i = 0;siteNames.size()>i;i++){
        ModelSite site = sitesRepository.findByName(siteNames.get(i));
        if(site.getStatus()!=Status.FAILED){
          site.setStatus(Status.INDEXED);
          sitesRepository.save(site);
        }
      }
      isIndexing = false;
      return isSuccessful;
    }else{
      return !isSuccessful;
    }
  }

  public void startIndexing(){
    if(isFirstRun){
      for(int i = 0; i< initialConSites.getSites().size(); i++){
        siteNames.add(initialConSites.getSites().get(i).getName());
        try{
          int siteIdToDelete = sitesRepository.findByName(initialConSites.getSites().get(i).getName()).getId();
          pagesRepository.deleteBySiteId(siteIdToDelete);
          sitesRepository.deleteById(siteIdToDelete);
        }catch (Exception e) {
      }
        uncheckedCheckerLinks = new ArrayList<>();
        arguments = new ArrayList<>();
        arguments.add(initialConSites.getSites().get(i).getUrl());
        arguments.add("");
        arguments.add(String.valueOf(0));
        arguments.add(initialConSites.getSites().get(i).getName());
        arguments.add("");
        arguments.add(String.valueOf(true));
        arguments.add(String.valueOf(setLevel(url)));

        IndexingService task = IndexingService.builder()
            .arguments(arguments)
            .pagesRepository(pagesRepository)
            .sitesRepository(sitesRepository)
            .build();
        subFirstTasks.add(task);
        forkJoinPool.execute(task);
      }
      for (IndexingService task : subFirstTasks) {
        task.join();
      }
    }
  }

  @Override
  protected void compute() {
    findLinks();
  }

  public void findLinks() {
    if(!url.isEmpty() && isIndexing){
      ArrayList<Document> availableCheckedLinks = new ArrayList<>(isAvailable(url));
      ArrayList<String> validLinks = new ArrayList<>(isCorrectLink(availableCheckedLinks));
      savingLinks(validLinks);
    }
  }

  private ArrayList<Document> isAvailable(String url) {
    if (pagesRepository.countBySiteId(siteId) < numberOfLines && (level < depth || depth == 0)) {
      ArrayList<Document> AvailableLinks = new ArrayList<>();
      if (pagesRepository.findByPath(url)==null) {
        try {
          doc2 = Connect(url).parse();
          AvailableLinks.add(doc2);
        } catch (Exception e) {
        }
        SaveSite();
        SavePage();
        uncheckedCheckerLinks.remove(url);
        return AvailableLinks;
      } else {
        uncheckedCheckerLinks.remove(url);
        return AvailableLinks;
      }
    }else{
      return new ArrayList<>();
    }
  }

  public Connection.Response Connect (String url){
    try {
      Thread.sleep(20000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    try {
      response = Jsoup.connect(url).timeout(10 * 2000).execute();
    } catch (Exception e) {
      uncheckedCheckerLinks.remove(url);
    }
    return response;
  }

  public void SaveSite(){
    if(isFirstRun){
      ModelSite modelSite = new ModelSite();
      modelSite.setUrl(url);
      modelSite.setName(name);
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
        }catch(Exception e) {
        }
      }
      sitesRepository.save(modelSite);
      siteId = sitesRepository.findByName(name).getId();
      baseUrl = url;
    }else{
      return;
    }
  }

  public void SavePage(){
    if (pagesRepository.countBySiteId(siteId) < numberOfLines && (level < depth || depth == 0)) {
      ModelPage modelPage = new ModelPage();
      modelPage.setModelSite(sitesRepository.findById(siteId).get());
      if(response!=null && response.statusCode()==200) {
        modelPage.setCode(response.statusCode());
        modelPage.setContent(String.valueOf(doc2));
      }else if(response==null){
        modelPage.setCode(000);
        modelPage.setContent("");
      }else{
        modelPage.setCode(response.statusCode());
        modelPage.setContent(String.valueOf(doc2));
        ModelSite modelSite = sitesRepository.findById(siteId).get();
        modelSite.setLastError(String.valueOf(doc2));
        modelSite.setStatus(Status.FAILED);
        sitesRepository.save(modelSite);
      }
      if(isFirstRun){
        modelPage.setPath("/");
      }else{
        modelPage.setPath(url.replace(baseUrl, ""));
      }
      pagesRepository.save(modelPage);
    }
  }

  private ArrayList<String> isCorrectLink(ArrayList DocumentsToCheck) {
    ArrayList<String> Links = new ArrayList<>();
    for (int i = 0; i < DocumentsToCheck.size(); i++) {
      doc3 = (Document) DocumentsToCheck.get(i);
      if (doc3 != null) {
        Elements subLinksHead = doc3.select("a");
        for (Element subLink : subLinksHead) {
          LinkCleaner(Links, subLink);
        }
      }
    }
    return Links;
  }

  public void LinkCleaner(ArrayList<String> Links, Element subLink){
    if (
        String.valueOf(subLink).matches(".*[\"']" + baseUrl + "/.*[\"']((.|\\n)*)")
            && !String.valueOf(subLink).matches(".*[\"']" + baseUrl + "/.*[.pdf][\"'].*")
    ) {
      String elementToClean2 = String.valueOf(subLink);
      String elementAlmostClean2 = elementToClean2.replaceAll("<.+href=[\"']", "");
      String elementClean2 = elementAlmostClean2.replaceAll("/?[\"']((.|\\n)*)", "");
      Links.add(elementClean2);
    } else if (
        String.valueOf(subLink).matches(".+href=[\"']/[A-Za-z0-9]+.*[\"']((.|\\n)*)")
            && !String.valueOf(subLink)
            .matches(".+href=[\"']/[A-Za-z0-9]+.*[.pdf][\"']((.|\\n)*)")
    ) {
      String elementToClean3 = String.valueOf(subLink);
      String elementAlmostClean3 = elementToClean3.replaceAll("<.+href=[\"']", "");
      String elementClean3 = elementAlmostClean3.replaceAll("/?[\"']((.|\\n)*)", "");
      Links.add(baseUrl + elementClean3);
    } else {
//            System.out.println("Ссылка " + subLink + " не подошла.");
    }
  }

  private void savingLinks(ArrayList linksToSave) {
    if(pagesRepository.countBySiteId(siteId) < numberOfLines){
      for (int i = 0; i < linksToSave.size(); i++) {
        if (!uncheckedCheckerLinks.contains(linksToSave.get(i))
            && pagesRepository.findByPath(linksToSave.get(i).toString().replace(baseUrl, ""))==null)
        {
          arguments = new ArrayList<>();
          arguments.add((String) linksToSave.get(i));
          arguments.add(baseUrl);
          arguments.add(String.valueOf(siteId));
          arguments.add("");
          arguments.add("");
          arguments.add(String.valueOf(false));
          arguments.add(String.valueOf(setLevel(url)));

          IndexingService task = IndexingService.builder()
              .arguments(arguments)
              .pagesRepository(pagesRepository)
              .sitesRepository(sitesRepository)
              .build();
          subTasks.add(task);
          forkJoinPool.execute(task);
          uncheckedCheckerLinks.add((String) linksToSave.get(i));
        } else {
//            System.out.println("Ссылка " + linksToSave.get(i) + " некорректна.");
        }
      }
      for (IndexingService task : subTasks) {
        task.join();
      }
    }
  }

  public boolean stopIndexing(){
    if(isIndexing){
      for (IndexingService task : subTasks) {
        task.cancel(true);
      }
      for (IndexingService task : subFirstTasks) {
        task.cancel(true);
      }
      List<ModelSite> sitesToFail = sitesRepository.findAllByStatus(String.valueOf(Status.INDEXING));
      for(int i = 0;sitesToFail.size()>i;i++){
        try {
          ModelSite modelSite = sitesToFail.get(i);
          modelSite.setStatus(Status.FAILED);
          modelSite.setLastError("Индексация остановлена пользователем");
          modelSite.setStatusTime(LocalDateTime.now());
          sitesRepository.save(modelSite);
        }catch (Exception e) {
        }
      }
      isIndexing = false;
      return true;
    }else{
      return false;
    }
  }

  private Integer setLevel(String url) {
    String linkLevel = url.replaceAll("[^/]", "");
    level = linkLevel.length() - 2;
    return level;
  }
}
