package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Site;

public interface SitesRepository extends JpaRepository<Site, Long> {

}
