package searchengine.services;

import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import searchengine.model.ModelSite;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final Random random = new Random();
    private final SitesList sites;
    ZoneId zoneId = ZoneId.systemDefault();
    private final PagesRepository pagesRepository;
    private final SitesRepository sitesRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for(int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            ModelSite modelSite = sitesRepository.findByName(site.getName());
            if(modelSite==null){
            }else{
            DetailedStatisticsItem item = creatingDetailedStatisticsItem(site, modelSite);
            total.setPages(total.getPages() + item.getPages());
            total.setLemmas(total.getLemmas() + item.getLemmas());
            detailed.add(item);
            }
        }
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private DetailedStatisticsItem creatingDetailedStatisticsItem(Site site, ModelSite modelSite){
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        int siteId = sitesRepository.findIdByUrl(site.getUrl());
        int pages = pagesRepository.countBySiteId(siteId);
        int lemmas = lemmaRepository.countLemmasBySiteId(siteId);
        item.setPages(pages);
        item.setLemmas(lemmas);
        item.setStatus(String.valueOf(modelSite.getStatus()));
        item.setError(modelSite.getLastError());
        item.setStatusTime(modelSite.getStatusTime().atZone(zoneId).toInstant().toEpochMilli());
        return item;
    }
}
