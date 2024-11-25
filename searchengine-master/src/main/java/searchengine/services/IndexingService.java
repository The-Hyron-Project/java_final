package searchengine.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import searchengine.config.ConnectionProperties;
import searchengine.config.SitesList;
import searchengine.dto.statistics.RequestResponceFailed;
import searchengine.dto.statistics.RequestResponceSucceeded;
import searchengine.dto.statistics.RequestResponse;
import searchengine.model.ModelPage;
import searchengine.model.ModelSite;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

@AllArgsConstructor
@Service
public class IndexingService extends RecursiveAction {

  @Autowired
  public PagesRepository pagesRepository;
  @Autowired
  public SitesRepository sitesRepository;
  @Autowired
  public IndexRepository indexRepository;
  @Autowired
  public LemmaRepository lemmaRepository;
  @Autowired
  public SitesList initialConSites;
  @Autowired
  public ConnectionProperties connectionProperties;
  @Autowired
  PageIndexingService pageIndexingService;

  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(1);

  public String userAgent;
  public String referrer;
  public String timeout;

  int level;
  static int numberOfLines = 10;
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
  Boolean isStarted = false;
  Connection.Response response = null;
  public Boolean isFirstRun = true;
  public static Boolean isIndexing = false;
  static List<String> siteNames = new ArrayList<>();
  public static ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
  public List<String> arguments;
  public List<CrudRepository> repArguments;
  RequestResponceFailed requestResponseFailed;
  RequestResponceSucceeded requestResponceSucceeded;

  public IndexingService() {}

  @Builder
  public IndexingService(List<String> arguments, List<CrudRepository> repArguments,PageIndexingService pageIndexingService ) {
    this.url = arguments.get(0);
    this.baseUrl = arguments.get(1);
    this.siteId = Integer.parseInt(arguments.get(2));
    this.name=arguments.get(3);
    this.isFirstRun= Boolean.valueOf(arguments.get(5));
    this.level = Integer.parseInt(arguments.get(6));
    this.userAgent = arguments.get(7);
    this.referrer = arguments.get(8);
    this.timeout = arguments.get(9);
    this.sitesRepository = (SitesRepository) repArguments.get(0);
    this.pagesRepository = (PagesRepository) repArguments.get(1);
    this.indexRepository = (IndexRepository) repArguments.get(2);
    this.lemmaRepository = (LemmaRepository) repArguments.get(3);
    this.pageIndexingService = pageIndexingService;
//    this.initialConSites=initialConSites;
  }

  @Bean
  public void flagChecker(){
    Runnable collection = () -> {
      if(isIndexing && !isStarted){
        isStarted = true;
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
        isStarted = false;
      }
    };
    ScheduledFuture<?> collectionHandle =
        scheduler.scheduleAtFixedRate(collection, 0, 5, TimeUnit.SECONDS);
  }

  public RequestResponse getIndexResult() {
    if(!isIndexing){
      isIndexing = true;
      return requestResponceSucceeded = new RequestResponceSucceeded(true);
    }else{
      return requestResponseFailed = new RequestResponceFailed(false, "Индексация уже запущена");
    }
  }

//  сократить
  public void startIndexing(){
    if(isFirstRun){
      for(int i = 0; i< initialConSites.getSites().size(); i++){
        siteNames.add(initialConSites.getSites().get(i).getName());
        try{
          int siteIdToDelete = sitesRepository.findByName(initialConSites.getSites().get(i).getName()).getId();
          List<Integer> pagesIds = pagesRepository.findAllPagesIdsBySiteId(siteIdToDelete);
          for(int id : pagesIds){
            indexRepository.deleteIndexByPageId(id);
          }
          lemmaRepository.deleteLemmaBySiteId(siteIdToDelete);
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
        arguments.add(connectionProperties.getUserAgent());
        arguments.add(connectionProperties.getReferrer());
        arguments.add(connectionProperties.getTimeout());

        repArguments = new ArrayList<>();
        repArguments.add(sitesRepository);
        repArguments.add(pagesRepository);
        repArguments.add(indexRepository);
        repArguments.add(lemmaRepository);

        IndexingService task = IndexingService.builder()
            .arguments(arguments)
            .repArguments(repArguments)
            .pageIndexingService(pageIndexingService)
//            .initialConSites(initialConSites)
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
      response = Jsoup.connect(url)
        .userAgent(userAgent)
        .referrer(referrer)
        .timeout(Integer.parseInt(timeout))
        .execute();
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

  //  сократить
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
        if(!(response==null) && !(modelPage.getContent().isEmpty())){
          sitesRepository.save(modelSite);
        }
      }
      if(isFirstRun){
        modelPage.setPath("/");
      }else{
        modelPage.setPath(url.replace(baseUrl, ""));
      }
      pagesRepository.save(modelPage);
      if(!String.valueOf(response.statusCode()).matches("[4|5].*") && !modelPage.getContent().isBlank()){
          if(isFirstRun){
            pageIndexingService.startPageIndexing(url+"/");
          }else{
            pageIndexingService.startPageIndexing(url);
          }
      }
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

  //  сократить
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
          arguments.add(userAgent);
          arguments.add(referrer);
          arguments.add(timeout);

          repArguments = new ArrayList<>();
          repArguments.add(sitesRepository);
          repArguments.add(pagesRepository);
          repArguments.add(indexRepository);
          repArguments.add(lemmaRepository);

          IndexingService task = IndexingService.builder()
              .arguments(arguments)
              .repArguments(repArguments)
              .pageIndexingService(pageIndexingService)
              .build();
          subTasks.add(task);
          forkJoinPool.execute(task);
          uncheckedCheckerLinks.add((String) linksToSave.get(i));
        } else {
        }
      }
      for (IndexingService task : subTasks) {
        task.join();
      }
    }
  }

  public RequestResponse stopIndexing(){
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
      isStarted = false;

      return requestResponceSucceeded = new RequestResponceSucceeded(true);
    }else{
      return requestResponseFailed = new RequestResponceFailed(false, "Индексация не запущена");
    }
  }

  private Integer setLevel(String url) {
    String linkLevel = url.replaceAll("[^/]", "");
    level = linkLevel.length() - 2;
    return level;
  }
}
