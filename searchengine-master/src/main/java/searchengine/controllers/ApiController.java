package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.PageIndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final PageIndexingService pageIndexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService,
        PageIndexingService pageIndexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.pageIndexingService = pageIndexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startindexing")
    public ResponseEntity<String> startIndexing() {
        if (indexingService.getIndexResult()) {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': false\n"
                + "\'error\': \"Индексация уже запущена\"");
        }
        else {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': true");
        }
    }

    @GetMapping("/stopindexing")
    public ResponseEntity<String> stopIndexing() {
        if (!indexingService.stopIndexing()) {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': false\n"
                + "\'error\': \"Индексация не запущена\"");
        }
        else {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': true");
        }
    }

    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url)  {
        if (!pageIndexingService.startPageIndexing(url)) {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': false\n"
                + "\'error\': \"Данная страница находится за пределами сайтов, \n"
                + "указанных в конфигурационном файле\n\"");
        }
        else {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': true");
        }
    }
}
