package com.example.cinepick.service;

import com.example.cinepick.domain.Movie;
import com.example.cinepick.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 크롤링으로 모은 예매사별 상세 링크를 {@link Movie} 에 반영한다.
 *
 * <p>{@link MainPageCatalog}(크롤링 직후 일괄)와 {@link MovieService#getMovieDetail}(상세 화면)
 * 두 곳이 쓴다. 한쪽에 두고 다른 쪽이 부르면 <b>순환 의존</b>이 된다 —
 * {@code MovieCrawlerScheduler → MainPageCatalog} 이고 {@code MovieService → MovieCrawlerScheduler}
 * 라서, 이 기능을 둘 중 어디에 둬도 고리가 닫힌다. 그래서 양쪽이 함께 의존하는 별도 빈으로 뺐다.
 */
@Service
@RequiredArgsConstructor
public class SiteLinkUpdater {

    private final MovieRepository movieRepository;

    /**
     * 값이 <b>바뀔 때만</b> 저장한다. 크롤링 주기 한 번당 링크가 달라지는 영화는 소수라,
     * 그 주기의 첫 호출에서 한 번 반영되면 그 뒤로는 DB 쓰기가 일어나지 않는다.
     *
     * @param links 해당 영화의 사이트별 링크. 없으면 아무것도 하지 않는다
     */
    public void apply(Movie movie, List<MovieCrawlerService.SiteLink> links) {
        if (movie == null || links == null) return;

        boolean changed = false;
        for (MovieCrawlerService.SiteLink link : links) {
            switch (link.siteName) {
                case "CGV":
                    if (!link.detailUrl.equals(movie.getCgvUrl())) {
                        movie.setCgvUrl(link.detailUrl);
                        changed = true;
                    }
                    break;
                case "Megabox":
                    if (!link.detailUrl.equals(movie.getMegaboxUrl())) {
                        movie.setMegaboxUrl(link.detailUrl);
                        changed = true;
                    }
                    break;
                case "LotteCinema":
                    if (!link.detailUrl.equals(movie.getLotteUrl())) {
                        movie.setLotteUrl(link.detailUrl);
                        changed = true;
                    }
                    break;
            }
        }

        if (changed) {
            movieRepository.save(movie);
        }
    }
}
