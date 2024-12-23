package searchengine.services;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import searchengine.config.Site;
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

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Service
public class IndexService extends RecursiveAction {

  private final PagesRepository pagesRepository;
  private final SitesRepository sitesRepository;
  private final IndexRepository indexRepository;
  private final LemmaRepository lemmaRepository;
  private final SitesList initialConSites;
  private final ConnectionProperties connectionProperties;
  private final PageIndexService pageIndexService;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private String userAgent;
  private String referrer;
  private String timeout;
  private int level;
  private int NUMBEROFLINES = 0;
  private int DEPTH = 0;
  private String url = "";
  private String name = "";
  private String baseUrl = "";
  private Document doc2 = null;
  private Document doc3 = null;
  private static ArrayList<String> uncheckedCheckerLinks;
  private List<IndexService> subTasks = new ArrayList<>();
  private List<IndexService> subFirstTasks = new ArrayList<>();;
  private int siteId;
  private static AtomicBoolean isStarted = new AtomicBoolean(false);
  private Connection.Response response = null;
  private Boolean isFirstRun = true;
  private static volatile AtomicBoolean isIndexing = new AtomicBoolean(false);
  private static List<String> siteNames = new ArrayList<>();
  private static ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
  private List<String> arguments;
  private List<CrudRepository> repArguments;
  private RequestResponceFailed requestResponseFailed;
  private RequestResponceSucceeded requestResponceSucceeded;
  private String errorMessage;

  private IndexService(List<String> arguments, List<CrudRepository> repArguments,
      PageIndexService pageIndexService, SitesList initialConSites, ConnectionProperties connectionProperties) {
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
    this.pageIndexService = pageIndexService;
    this.initialConSites = initialConSites;
    this.connectionProperties = connectionProperties;

  }

  @Bean
  private void flagChecker(){
    Runnable collection = () -> {
      if(isIndexing.get() && !isStarted.get()) {
        startAction();
      }
    };
    ScheduledFuture<?> collectionHandle =
        scheduler.scheduleAtFixedRate(collection, 0, 5, TimeUnit.SECONDS);
  }

  private void startAction(){
    isStarted.set(true);
    if(isStarted.get()){
      startIndex();
    }
    if(isStarted.get()) {
      setIndexedStatus();
    }
    if(!isIndexing.get()){
      killThreads();
    }
    if(isIndexing.get()){
      log.info("Indexing is finished");
    }
    isIndexing.set(false);
    isStarted.set(false);
  }

  private void setIndexedStatus(){
    for(int i = 0; siteNames.size() > i; i++){
      ModelSite site = sitesRepository.findByName(siteNames.get(i));
      if(site.getStatus() != Status.FAILED){
        site.setStatus(Status.INDEXED);
        sitesRepository.save(site);
      }
    }
  }

  private void killThreads(){
    for (IndexService task : subTasks) {
      task.cancel(true);
    }
    for (IndexService task : subFirstTasks) {
      task.cancel(true);
    }
  }

  public RequestResponse getIndexResult() {
    if(!isIndexing.get()){
      isIndexing.set(true);
      return requestResponceSucceeded = new RequestResponceSucceeded(true);
    }else{
      return requestResponseFailed = new RequestResponceFailed(false, "Индексация уже запущена");
    }
  }

  private void startIndex(){
    log.info("Indexing is started");
    if(isFirstRun){
      performFirstRun();
    }
  }

  private void performFirstRun(){
    for(int i = 0; i < initialConSites.getSites().size(); i++){
      firstRunPerSite(initialConSites.getSites().get(i));
    }
    joinSubFirstTasks();
  }

  private void firstRunPerSite(Site site){
    siteNames.add(site.getName());
    try{
      int siteIdToDelete = sitesRepository.findByName(site.getName()).getId();
      List<Integer> pagesIds = pagesRepository.findAllPagesIdsBySiteId(siteIdToDelete);
      deleteOldData(siteIdToDelete, pagesIds);
    } catch (Exception e) {
      log.trace("Your DB may be empty");
      log.trace(e.getMessage());
    }
    uncheckedCheckerLinks = new ArrayList<>();
    arguments = new ArrayList<>(List.of(site.getUrl(), "", String.valueOf(0), site.getName(), "", String.valueOf(true), String.valueOf(setLevel(url)), connectionProperties.getUserAgent(), connectionProperties.getReferrer(), connectionProperties.getTimeout()));
    repArguments = new ArrayList<>(List.of(sitesRepository, pagesRepository, indexRepository, lemmaRepository));
    IndexService task = new IndexService(arguments, repArguments, pageIndexService, initialConSites, connectionProperties);
    if(isIndexing.get()) {
      subFirstTasks.add(task);
    }
    if(isIndexing.get()) {
      forkJoinPool.execute(task);
    }
  }

  private void deleteOldData(int siteIdToDelete, List<Integer> pagesIds){
    for(int id : pagesIds){
      indexRepository.deleteIndexByPageId(id);
    }
    lemmaRepository.deleteLemmaBySiteId(siteIdToDelete);
    pagesRepository.deleteBySiteId(siteIdToDelete);
    sitesRepository.deleteById(siteIdToDelete);
  }

  private void joinSubFirstTasks(){
    for (IndexService task : subFirstTasks) {
      if(isIndexing.get()){
        task.join();
      }
    }
  }

  @Override
  protected void compute() {
    findLinks();
  }

  private void findLinks() {
    if(!url.isEmpty() && isIndexing.get()){
      ArrayList<Document> availableCheckedLinks = new ArrayList<>(isAvailable(url));
      ArrayList<String> validLinks = new ArrayList<>(isCorrectLink(availableCheckedLinks));
      processLinks(validLinks);
    }
  }

  private ArrayList<Document> isAvailable(String url) {
    if ((pagesRepository.countBySiteId(siteId) < NUMBEROFLINES || NUMBEROFLINES == 0) && (level < DEPTH || DEPTH == 0)) {
      return checkLink(url);
    }else{
      return new ArrayList<>();
    }
  }

  private ArrayList<Document> checkLink(String url){
    ArrayList<Document> AvailableLinks = new ArrayList<>();
    if (pagesRepository.findByPath(url) == null) {
      try {
        Connection.Response responseLocal = Connect(url);
        if(responseLocal != null){
          doc2 = responseLocal.parse();
          AvailableLinks.add(doc2);
        }
      } catch (IOException e) {
        log.trace("Site is not available");
        log.trace(e.getMessage());
      }
      SaveSite();
      SavePage();
      uncheckedCheckerLinks.remove(url);
      return AvailableLinks;
    } else {
      uncheckedCheckerLinks.remove(url);
      return AvailableLinks;
    }
  }

  private Connection.Response Connect (String url){
    try {
      Thread.sleep(20000);
    } catch (InterruptedException e) {
      log.trace("Sleep was interrupted");
      log.trace(e.getMessage());
    }
    try {
      response = Jsoup.connect(url)
        .userAgent(userAgent)
        .referrer(referrer)
        .timeout(Integer.parseInt(timeout))
        .execute();
    } catch (IOException e) {
      uncheckedCheckerLinks.remove(url);
      log.trace("Site is not available");
      log.trace(e.getMessage());
      errorMessage = e.getMessage();
    }
    return response;
  }

  private void SaveSite(){
    if(isFirstRun){
      ModelSite modelSite = new ModelSite();
      modelSite.setUrl(url);
      modelSite.setName(name);
      modelSite.setStatusTime(LocalDateTime.now());
      if(response != null) {
        modelSite.setLastError("");
        modelSite.setStatus(Status.INDEXING);
      } else{
        modelSite.setStatus(Status.FAILED);
        modelSite.setLastError(errorMessage);
      }
      sitesRepository.save(modelSite);
      siteId = sitesRepository.findByName(name).getId();
      baseUrl = url;
    }else{
    }
  }

  private void SavePage() {
    if ((pagesRepository.countBySiteId(siteId) < NUMBEROFLINES || NUMBEROFLINES == 0) && (level < DEPTH || DEPTH == 0) && response != null) {
      ModelPage modelPage = new ModelPage();
      modelPage.setModelSite(sitesRepository.findById(siteId).get());
      modelPage.setCode(response.statusCode());
      modelPage.setContent(String.valueOf(doc2));
      if (isFirstRun) {
        modelPage.setPath("/");
      } else {
        modelPage.setPath(url.replace(baseUrl, ""));
      }
      pagesRepository.save(modelPage);
      startPageIndexing(modelPage);
    }
  }

  private void startPageIndexing(ModelPage modelPage){
    if (!String.valueOf(response.statusCode()).matches("[4|5].*") && !modelPage.getContent().isBlank()) {
      List<CrudRepository> repArguments = new ArrayList<>(List.of(sitesRepository, pagesRepository, indexRepository, lemmaRepository));
      PageIndexService pageIndexService2 = new PageIndexService(repArguments, connectionProperties, initialConSites);
      if (isFirstRun) {
        pageIndexService2.startPageIndex(url + "/");
      } else {
        pageIndexService2.startPageIndex(url);
      }
    }
  }

  private ArrayList<String> isCorrectLink(ArrayList<Document> DocumentsToCheck) {
    ArrayList<String> links = new ArrayList<>();
    for (int i = 0; i < DocumentsToCheck.size(); i++) {
      links.addAll(collectLinks(DocumentsToCheck.get(i)));
    }
    return links;
  }

  private ArrayList<String> collectLinks(Document receivedDocument){
    ArrayList<String> linksLocal = new ArrayList<>();
    doc3 = receivedDocument;
    if (doc3 != null) {
      Elements subLinksHead = doc3.select("a");
      for (Element subLink : subLinksHead) {
        linkCleaner(linksLocal, subLink);
      }
    }
    return linksLocal;
  }

  private void linkCleaner(ArrayList<String> Links, Element subLink){
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

  private void processLinks(ArrayList<String> linksToSave) {
    if((isIndexing.get() && pagesRepository.countBySiteId(siteId) < NUMBEROFLINES || NUMBEROFLINES == 0)){
      for (int i = 0; i < linksToSave.size(); i++) {
        generateThread(linksToSave.get(i));
      }
      joinTasks();
    }
  }

  private void generateThread(String link){
    if (!uncheckedCheckerLinks.contains(link)
        && pagesRepository.findByPath(link.toString().replace(baseUrl, ""))==null)
    {
      arguments = new ArrayList<>(List.of(link, baseUrl, String.valueOf(siteId), "", "", String.valueOf(false), String.valueOf(setLevel(url)), userAgent, referrer, timeout));
      repArguments = new ArrayList<>(List.of(sitesRepository, pagesRepository, indexRepository, lemmaRepository));
      IndexService task = new IndexService(arguments, repArguments, pageIndexService, initialConSites, connectionProperties);
      if(isIndexing.get()) {
        subTasks.add(task);
      }
      if(isIndexing.get()) {
        forkJoinPool.execute(task);
      }
      uncheckedCheckerLinks.add(link);
    }
  }

  private void joinTasks(){
    for (IndexService task : subTasks) {
      if(isIndexing.get()){
        task.join();
      }
    }
  }

  public RequestResponse stopIndexing(){
    if(isIndexing.get()){
      List<ModelSite> sitesToFail = sitesRepository.findAllByStatus(String.valueOf(Status.INDEXING));
      for(int i = 0; sitesToFail.size() > i; i++){
        try {
          ModelSite modelSite = sitesToFail.get(i);
          modelSite.setStatus(Status.FAILED);
          modelSite.setLastError("Индексация остановлена пользователем");
          modelSite.setStatusTime(LocalDateTime.now());
          sitesRepository.save(modelSite);
        } catch (Exception e) {
          log.trace("Your DB may be empty");
          log.trace(e.getMessage());        }
      }
      isIndexing.set(false);
      isStarted.set(false);
      log.info("Indexing is stopped");
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
