package com.example.cinepick.service;

import com.example.cinepick.controller.SurveyController;
import com.example.cinepick.domain.Member;
import com.example.cinepick.domain.Movie;
import com.example.cinepick.domain.MovieRating;
import com.example.cinepick.domain.PreferenceSurvey;
import com.example.cinepick.domain.Reservation;
import com.example.cinepick.domain.ReservationStatus;
import com.example.cinepick.dto.request.SurveyReqDto;
import com.example.cinepick.dto.response.CurationResDto;
import com.example.cinepick.repository.MovieRatingRepository;
import com.example.cinepick.repository.PreferenceSurveyRepository;
import com.example.cinepick.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 사용자 취향의 저장과 분석.
 * 점수는 설문·예매·평점 세 출처의 합산이며, 출처를 늘리려면 scoreBySource() 에 한 줄만 더하면 된다.
 * 검색 이력은 쓰지 않는다 — 제목 검색이라 검색어에서 장르를 추론할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final PreferenceSurveyRepository preferenceSurveyRepository;
    private final ReservationRepository reservationRepository;
    private final MovieRatingRepository movieRatingRepository;
    private final MoodTagger moodTagger;

    private static final int MAX_RANK = 3;

    /**
     * 영화 한 편이 한 차원(장르 또는 분위기)에 갖는 표.
     * 이 예산을 축 개수로 나눠 담으므로 장르가 많이 붙은 영화든 적은 영화든 한 편의 영향은 같다.
     * 장르와 분위기를 합치면 한 편은 최대 4점(배율 1.0 기준), 6편쯤이면 설문 총점과 대등해진다.
     */
    private static final double MOVIE_BUDGET = 2.0;

    /**
     * 합성 결과를 담을 눈금. 정규화하면 값이 −1~1 이라 그대로 두면 {@code genreScores()} 가
     * 반올림할 때 전부 0 이나 ±1 로 뭉개진다. 100 을 곱해 −100~100 으로 펴둔다.
     */
    private static final double SCORE_SCALE = 100.0;

    // 점수 출처 이름. 화면의 비중 막대 범례에 그대로 쓰인다
    private static final String SOURCE_SURVEY = "설문";
    private static final String SOURCE_RESERVATION = "예매";
    private static final String SOURCE_RATING = "평점";

    /** 비중 막대에서 출처별로 쓸 색. 화면에 분기문을 두지 않도록 서버가 정한다 */
    private static final Map<String, String> SOURCE_COLORS = Map.of(
            SOURCE_SURVEY, "#FA5252",
            SOURCE_RESERVATION, "#4DABF7",
            SOURCE_RATING, "#9775FA"
    );
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * TMDB 장르(=movie.genre 에 저장된 값)를 설문 장르 8종으로 묶는 표.
     *
     * TMDB 는 18종을 내려주는데 설문은 8종만 받는다. 이 표가 없으면 「범죄, 미스터리」처럼
     * 설문에 없는 장르만 가진 영화는 장르 점수가 0 이 되어 스릴러를 좋아하는 회원에게도 매칭되지 않는다.
     * 여기 없는 장르(액션·드라마·코미디·스릴러·로맨스·SF·판타지)는 이름이 같아 그대로 쓴다.
     */
    private static final Map<String, String> GENRE_GROUP = Map.ofEntries(
            Map.entry("공포", "호러"),        // 이름만 다른 동의어
            Map.entry("모험", "액션"),
            Map.entry("전쟁", "액션"),
            Map.entry("서부", "액션"),
            Map.entry("미스터리", "스릴러"),
            Map.entry("범죄", "스릴러"),
            Map.entry("역사", "드라마"),
            Map.entry("다큐멘터리", "드라마"),
            Map.entry("TV 영화", "드라마"),
            Map.entry("가족", "코미디"),
            Map.entry("애니메이션", "판타지"),
            Map.entry("음악", "로맨스")
    );

    /**
     * 영화 데이터 쪽 장르명을 설문 장르명으로 맞춘다.
     * 선호 점수 맵은 설문 장르명을 키로 쓰므로 영화 장르를 여기 통과시킨 뒤 조회해야 한다.
     */
    public static String canonicalGenre(String genre) {
        if (genre == null) return null;
        String trimmed = genre.trim();
        return GENRE_GROUP.getOrDefault(trimmed, trimmed);
    }

    // 장르/분위기 표시용 아이콘·색상. 템플릿에 분기문을 두지 않으려고 서버에서 정한다
    private static final Map<String, String> GENRE_ICONS = Map.of(
            "액션", "bi bi-lightning-fill",
            "드라마", "bi bi-mask",
            "코미디", "bi bi-emoji-laughing",
            "스릴러", "bi bi-eye-fill",
            "호러", "bi bi-emoji-dizzy",
            "로맨스", "bi bi-heart-fill",
            "SF", "bi bi-rocket-fill",
            "판타지", "bi bi-stars"
    );
    private static final Map<String, String> GENRE_COLORS = Map.of(
            "액션", "#FA5252",
            "드라마", "#6F42C1",
            "코미디", "#F1C40F",
            "스릴러", "#16A085",
            "호러", "#212529",
            "로맨스", "#FF6B9D",
            "SF", "#3498DB",
            "판타지", "#8E44AD"
    );
    private static final Map<String, String> MOOD_ICONS = Map.of(
            "유쾌", "bi bi-emoji-smile-fill",
            "긴장", "bi bi-lightning-charge-fill",
            "슬픔", "bi bi-emoji-frown-fill",
            "카타르시스", "bi bi-stars",
            "신비", "bi bi-eye-fill"
    );
    private static final Map<String, String> MOOD_COLORS = Map.of(
            "유쾌", "#FD7E14",
            "긴장", "#FA5252",
            "슬픔", "#4DABF7",
            "카타르시스", "#9775FA",
            "신비", "#20C997"
    );

    // ---------------------------------------------------------------- 저장

    /**
     * 설문 팝업과 취향 수정 화면이 공유하는 저장 로직. 보내는 모양이 다르다.
     * - 팝업: 장르/분위기 3개씩. 고른 순서가 순위, 축 값은 100/70/40
     * - 수정 화면: 축 13개 값 전체. 순위는 상위 3개에서 파생
     */
    @Transactional
    public boolean saveSurvey(SurveyReqDto reqDto, Member member) {
        boolean hasRanks = reqDto.getGenres() != null && reqDto.getGenres().size() == MAX_RANK
                && reqDto.getMoods() != null && reqDto.getMoods().size() == MAX_RANK;
        boolean hasAxes = reqDto.getAxisNames() != null && reqDto.getAxisValues() != null
                && !reqDto.getAxisNames().isEmpty()
                && reqDto.getAxisNames().size() == reqDto.getAxisValues().size();

        if (!hasRanks && !hasAxes) return false;

        PreferenceSurvey survey = preferenceSurveyRepository.findByMemberId(member.getId())
                .orElseGet(PreferenceSurvey::new);
        survey.setMember(member);

        if (hasAxes) {
            applyAxisValues(survey, reqDto);
        } else {
            applyRanks(survey, reqDto.getGenres(), reqDto.getMoods());
        }

        // 설문 팝업은 슬라이더 값을 보내지 않는다. null 이면 기존 설정을 그대로 둔다
        if (reqDto.getSurveyWeight() != null) {
            survey.setSurveyWeight(clampToPercent(reqDto.getSurveyWeight()));
        }
        if (reqDto.getGenreDiversity() != null) {
            survey.setGenreDiversity(clampToPercent(reqDto.getGenreDiversity()));
        }
        if (reqDto.getMoodIntensity() != null) {
            survey.setMoodIntensity(clampToPercent(reqDto.getMoodIntensity()));
        }

        preferenceSurveyRepository.save(survey);
        return true;
    }

    /** 팝업 경로: 고른 3개를 순위로 세우고 축 값을 100/70/40 으로 깔아둔다 */
    private void applyRanks(PreferenceSurvey survey, List<String> genres, List<String> moods) {
        survey.setGenreRank1(genres.get(0));
        survey.setGenreRank2(genres.get(1));
        survey.setGenreRank3(genres.get(2));
        survey.setMoodRank1(moods.get(0));
        survey.setMoodRank2(moods.get(1));
        survey.setMoodRank3(moods.get(2));

        Map<String, Integer> axes = new LinkedHashMap<>();
        for (String option : SurveyController.GENRE_OPTIONS) axes.put(option, 0);
        for (String option : SurveyController.MOOD_OPTIONS) axes.put(option, 0);
        for (int i = 0; i < MAX_RANK; i++) {
            axes.put(genres.get(i), PreferenceSurvey.seedValueForRank(i + 1));
            axes.put(moods.get(i), PreferenceSurvey.seedValueForRank(i + 1));
        }

        // 컬렉션을 통째로 교체하지 않고 비운 뒤 채운다.
        // Hibernate 가 관리하는 인스턴스를 갈아치우면 고아 삭제가 꼬인다
        survey.getAxisValues().clear();
        survey.getAxisValues().putAll(axes);
    }

    /** 수정 화면 경로: 축 값을 그대로 저장하고 순위는 상위 3개에서 파생시킨다 */
    private void applyAxisValues(PreferenceSurvey survey, SurveyReqDto reqDto) {
        Map<String, Integer> incoming = new LinkedHashMap<>();
        List<String> names = reqDto.getAxisNames();
        List<Integer> values = reqDto.getAxisValues();

        for (int i = 0; i < names.size(); i++) {
            Integer value = values.get(i);
            if (value == null) continue;
            incoming.put(names.get(i), clampToPercent(value));
        }

        Map<String, Integer> axes = new LinkedHashMap<>();
        for (String option : SurveyController.GENRE_OPTIONS) {
            axes.put(option, incoming.getOrDefault(option, survey.axisValue(option)));
        }
        for (String option : SurveyController.MOOD_OPTIONS) {
            axes.put(option, incoming.getOrDefault(option, survey.axisValue(option)));
        }

        survey.getAxisValues().clear();
        survey.getAxisValues().putAll(axes);

        List<String> topGenres = topThree(axes, SurveyController.GENRE_OPTIONS);
        survey.setGenreRank1(topGenres.get(0));
        survey.setGenreRank2(topGenres.get(1));
        survey.setGenreRank3(topGenres.get(2));

        List<String> topMoods = topThree(axes, SurveyController.MOOD_OPTIONS);
        survey.setMoodRank1(topMoods.get(0));
        survey.setMoodRank2(topMoods.get(1));
        survey.setMoodRank3(topMoods.get(2));
    }

    /**
     * 값이 큰 순서로 3개. 순위 컬럼이 nullable=false 라 값이 전부 0 이어도 3개를 채워야 하므로
     * 동점은 옵션 목록 순서로 가른다.
     */
    private List<String> topThree(Map<String, Integer> axes, List<String> options) {
        return options.stream()
                .sorted(Comparator.comparingInt((String o) -> axes.getOrDefault(o, 0)).reversed())
                .limit(MAX_RANK)
                .toList();
    }

    private int clampToPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    // ---------------------------------------------------------------- 분석

    /** 회원의 설문 정보. 없으면 비어 있다. 추천 로직이 슬라이더 값까지 함께 보기 위해 필요 */
    @Transactional(readOnly = true)
    public Optional<PreferenceSurvey> findSurvey(Member member) {
        return preferenceSurveyRepository.findByMemberId(member.getId());
    }

    /**
     * 장르 선호 점수. 키는 설문 장르명(액션·드라마·…·호러).
     * 영화의 장르를 {@link #canonicalGenre(String)} 에 통과시킨 뒤 조회할 것. 설문 전이면 빈 맵.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> genreScores(Member member, List<String> genreOptions) {
        PreferenceSurvey survey = preferenceSurveyRepository.findByMemberId(member.getId()).orElse(null);
        if (survey == null) return Map.of();

        return rounded(scoreOf(genreOptions, member, survey, true));
    }

    /** 분위기 선호 점수. 설문 전이면 빈 맵 */
    @Transactional(readOnly = true)
    public Map<String, Integer> moodScores(Member member, List<String> moodOptions) {
        PreferenceSurvey survey = preferenceSurveyRepository.findByMemberId(member.getId()).orElse(null);
        if (survey == null) return Map.of();

        return rounded(scoreOf(moodOptions, member, survey, false));
    }

    /**
     * 내부 계산은 소수를 쓰지만(예산을 축 개수로 나누므로) 바깥 API 는 정수로 넘긴다.
     * 모두 더한 뒤 마지막에 한 번만 반올림한다.
     */
    private Map<String, Integer> rounded(Map<String, Double> scores) {
        Map<String, Integer> result = new LinkedHashMap<>();
        scores.forEach((key, value) -> result.put(key, (int) Math.round(value)));
        return result;
    }

    /** 취향 분석 페이지에 필요한 값을 모두 계산해 돌려준다. 설문 전이면 null */
    @Transactional(readOnly = true)
    public CurationResDto buildCuration(Member member, List<String> genreOptions, List<String> moodOptions) {
        PreferenceSurvey survey = preferenceSurveyRepository.findByMemberId(member.getId()).orElse(null);
        if (survey == null) return null;

        // 출처별로 나눠 계산해두고 최종 점수는 합쳐서 만든다.
        // 비중 막대가 "설문 몇 점, 예매 몇 점"을 알아야 하기 때문이다
        Map<String, Map<String, Double>> genreBySource = scoreBySource(genreOptions, member, survey, true);
        Map<String, Map<String, Double>> moodBySource = scoreBySource(moodOptions, member, survey, false);

        Map<String, Double> genreScores = merge(genreOptions, genreBySource, survey.getSurveyWeight());
        Map<String, Double> moodScores = merge(moodOptions, moodBySource, survey.getSurveyWeight());

        // 장르는 비율 내림차순, 분위기는 레이더 차트 축이 흔들리지 않게 원래 순서 유지
        List<CurationResDto.PrefRow> genreRows = toRows(genreScores, survey, true);
        genreRows.sort(Comparator.comparingInt(CurationResDto.PrefRow::getPercent).reversed());
        List<CurationResDto.PrefRow> moodRows = toRows(moodScores, survey, false);

        // 조절판은 옵션 목록 순서를 그대로 쓴다. 슬라이더가 값에 따라 자리를 옮기면
        // 드래그하는 동안 손이 따라가지 못한다
        List<CurationResDto.AxisControl> genreControls = toControls(genreOptions, survey, true);
        List<CurationResDto.AxisControl> moodControls = toControls(moodOptions, survey, false);

        List<Reservation> confirmed = reservationRepository
                .findByMemberIdAndStatusOrderByDecidedAtDesc(member.getId(), ReservationStatus.CONFIRMED);
        List<MovieRating> ratings = movieRatingRepository.findByMemberId(member.getId());

        // 같은 영화를 여러 번 예매했어도 "예매 영화"는 한 편으로 센다
        long reservedMovieCount = confirmed.stream()
                .map(r -> r.getMovie().getId())
                .distinct()
                .count();

        return CurationResDto.builder()
                .reservedMovieCount((int) reservedMovieCount)
                .ratedCount(ratings.size())
                .averageRatingLabel(averageRatingLabel(ratings))
                .weights(buildWeights(genreBySource, moodBySource, survey.getSurveyWeight()))
                .updatedAtLabel(survey.getUpdatedAt().format(DATE_LABEL))
                .topGenreLabel(survey.getGenreRank1() + ", " + survey.getGenreRank2())
                .topMoodLabel(survey.getMoodRank1() + ", " + survey.getMoodRank2())
                .insightPattern(buildInsightPattern(survey))
                .insightEmotion(buildInsightEmotion(survey))
                .insightKeywords(List.of(survey.getGenreRank1(), survey.getMoodRank1(),
                        survey.getGenreRank2(), survey.getMoodRank2()))
                .genreRows(genreRows)
                .moodRows(moodRows)
                .genreControls(genreControls)
                .moodControls(moodControls)
                .surveyWeight(survey.getSurveyWeight())
                .genreDiversity(survey.getGenreDiversity())
                .moodIntensity(survey.getMoodIntensity())
                .sources(buildSources((int) reservedMovieCount, ratings.size()))
                .build();
    }

    /**
     * 설문·예매·평점이 판단에 얼마나 쓰이는지 백분율로 환산한다.
     * 설문 지분은 사용자가 정한 surveyWeight 그대로이고, 남은 몫을 예매와 평점이 영향력 크기로 나눈다.
     * 다만 이력이 없는 회원에게는 사실이 우선이라 설문 100% 로 표시한다.
     */
    private List<CurationResDto.SourceWeight> buildWeights(Map<String, Map<String, Double>> genreBySource,
                                                           Map<String, Map<String, Double>> moodBySource,
                                                           int surveyWeight) {
        // 영향력은 절댓값이다. 낮은 평점이 취향을 깎아내린 것도 "판단에 영향을 준 것"이므로
        // 크기로는 똑같이 센다. 방향은 net 의 부호로 따로 알린다
        double surveyInfluence = influenceOf(genreBySource, moodBySource, SOURCE_SURVEY);
        double reservationInfluence = influenceOf(genreBySource, moodBySource, SOURCE_RESERVATION);
        double ratingInfluence = influenceOf(genreBySource, moodBySource, SOURCE_RATING);
        double historyInfluence = reservationInfluence + ratingInfluence;

        if (surveyInfluence <= 0 && historyInfluence <= 0) return List.of();

        int surveyPercent;
        if (historyInfluence <= 0) {
            surveyPercent = 100;               // 관람 이력이 없으면 설문이 전부입니다
        } else if (surveyInfluence <= 0) {
            surveyPercent = 0;                 // 모든 축을 0으로 내리면 이력만 남습니다
        } else {
            surveyPercent = clampToPercent(surveyWeight);
        }

        int reservationPercent = historyInfluence <= 0
                ? 0
                : (int) Math.round((100 - surveyPercent) * reservationInfluence / historyInfluence);
        // 반올림 오차로 합이 100% 를 벗어나지 않도록 마지막 항목은 나머지로 채운다
        int ratingPercent = 100 - surveyPercent - reservationPercent;

        return List.of(
                weightOf(SOURCE_SURVEY, surveyPercent, surveyInfluence, genreBySource, moodBySource),
                weightOf(SOURCE_RESERVATION, reservationPercent, reservationInfluence, genreBySource, moodBySource),
                weightOf(SOURCE_RATING, ratingPercent, ratingInfluence, genreBySource, moodBySource)
        );
    }

    private double influenceOf(Map<String, Map<String, Double>> genreBySource,
                               Map<String, Map<String, Double>> moodBySource, String source) {
        return absSumOf(genreBySource.get(source)) + absSumOf(moodBySource.get(source));
    }

    private CurationResDto.SourceWeight weightOf(String source, int percent, double influence,
                                                 Map<String, Map<String, Double>> genreBySource,
                                                 Map<String, Map<String, Double>> moodBySource) {
        double net = sumOf(genreBySource.get(source)) + sumOf(moodBySource.get(source));
        return CurationResDto.SourceWeight.builder()
                .label(source)
                .colorHex(SOURCE_COLORS.get(source))
                .points((int) Math.round(influence))
                .percent(percent)
                .lowering(net < 0)
                .build();
    }

    private double sumOf(Map<String, Double> scores) {
        if (scores == null) return 0;
        return scores.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private double absSumOf(Map<String, Double> scores) {
        if (scores == null) return 0;
        return scores.values().stream().mapToDouble(Math::abs).sum();
    }

    /** 내가 남긴 평점의 평균. 아직 없으면 화면에 "-"로 표시 */
    private String averageRatingLabel(List<MovieRating> ratings) {
        if (ratings.isEmpty()) return "-";

        double average = ratings.stream().mapToInt(MovieRating::getScore).average().orElse(0);
        return String.format("%.1f", average);
    }

    /**
     * 항목별 선호 점수를 출처별로 따로 계산한다. <b>출처를 늘리는 확장 지점이다.</b>
     * 합치기 전 단계를 남기는 이유는 화면에 "설문 몇 %, 예매 몇 %"를 보여주기 위해서다.
     */
    private Map<String, Map<String, Double>> scoreBySource(List<String> options, Member member,
                                                           PreferenceSurvey survey, boolean isGenre) {
        // 예매와 평점은 같은 영화 목록 위에서 계산해야 하므로 한 번만 읽어 함께 넘긴다
        List<Movie> watched = watchedMovies(member);
        Map<Long, Integer> ratingByMovie = ratingsByMovie(member);

        Map<String, Map<String, Double>> bySource = new LinkedHashMap<>();

        Map<String, Double> surveyScores = emptyScores(options);
        addSurveyScore(surveyScores, survey);
        bySource.put(SOURCE_SURVEY, surveyScores);

        Map<String, Double> reservationScores = emptyScores(options);
        addReservationScore(reservationScores, watched, isGenre);
        bySource.put(SOURCE_RESERVATION, reservationScores);

        Map<String, Double> ratingScores = emptyScores(options);
        addRatingScore(ratingScores, watched, ratingByMovie, isGenre);
        bySource.put(SOURCE_RATING, ratingScores);

        return bySource;
    }

    /**
     * 출처별 점수를 합쳐 최종 선호 점수를 만든다. 단순 합산이 아니라 정규화 후 지분 합성이다.
     * <pre>최종 = SCORE_SCALE × ( w × (설문값/설문최고) + (1−w) × (이력점수/이력최고절댓값) )</pre>
     * <b>하한이 없어 음수가 그대로 나간다</b> — 화면은 {@link #toRows} 가 0 으로 자르고
     * 추천은 RecommendService 가 감점으로 쓴다.
     */
    private Map<String, Double> scoreOf(List<String> options, Member member, PreferenceSurvey survey, boolean isGenre) {
        return merge(options, scoreBySource(options, member, survey, isGenre), survey.getSurveyWeight());
    }

    private Map<String, Double> merge(List<String> options, Map<String, Map<String, Double>> bySource,
                                      int surveyWeight) {
        Map<String, Double> surveyScores = bySource.get(SOURCE_SURVEY);
        Map<String, Double> historyScores = historyOf(options, bySource);

        // 정규화 기준. 설문은 0 이상이라 최댓값이지만, 이력은 음수가 섞이므로 절댓값 기준이어야
        // 기피가 강한 축 하나 때문에 전체 부호가 뒤집히지 않는다
        double surveyTop = maxOf(surveyScores);
        double historyTop = maxAbsOf(historyScores);
        double w = surveyWeight / 100.0;

        Map<String, Double> total = emptyScores(options);
        for (String option : options) {
            double survey = surveyTop <= 0 ? 0 : surveyScores.get(option) / surveyTop;
            double history = historyTop <= 0 ? 0 : historyScores.get(option) / historyTop;
            total.put(option, SCORE_SCALE * (w * survey + (1 - w) * history));
        }
        return total;
    }

    /** 예매 + 평점을 합친 '관람 이력' 한 덩어리. 비중 합성은 설문 대 이력의 2파전이다 */
    private Map<String, Double> historyOf(List<String> options, Map<String, Map<String, Double>> bySource) {
        Map<String, Double> history = emptyScores(options);
        for (String source : List.of(SOURCE_RESERVATION, SOURCE_RATING)) {
            Map<String, Double> scores = bySource.get(source);
            if (scores != null) scores.forEach((key, value) -> history.merge(key, value, Double::sum));
        }
        return history;
    }

    private double maxOf(Map<String, Double> scores) {
        return scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    private double maxAbsOf(Map<String, Double> scores) {
        return scores.values().stream().mapToDouble(Math::abs).max().orElse(0);
    }

    /** 모든 항목을 0 으로 깔아둔 점수 맵. 이용 이력이 없는 항목도 화면에 0% 로 나와야 한다 */
    private Map<String, Double> emptyScores(List<String> options) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String option : options) scores.put(option, 0.0);
        return scores;
    }

    /** 확정 예매한 영화 목록. 같은 영화를 여러 번 예매해도 한 편으로 센다 */
    private List<Movie> watchedMovies(Member member) {
        Map<Long, Movie> distinct = new LinkedHashMap<>();
        for (Reservation reservation : reservationRepository
                .findByMemberIdAndStatusOrderByDecidedAtDesc(member.getId(), ReservationStatus.CONFIRMED)) {
            Movie movie = reservation.getMovie();
            if (movie != null) distinct.putIfAbsent(movie.getId(), movie);
        }
        return new ArrayList<>(distinct.values());
    }

    private Map<Long, Integer> ratingsByMovie(Member member) {
        Map<Long, Integer> scores = new LinkedHashMap<>();
        for (MovieRating rating : movieRatingRepository.findByMemberId(member.getId())) {
            if (rating.getMovie() != null) scores.put(rating.getMovie().getId(), rating.getScore());
        }
        return scores;
    }

    /**
     * 설문 점수: 사용자가 축마다 직접 정한 0~100 값을 그대로 쓴다.
     * 순위 3개에 9/6/3점을 주던 방식에서 바뀌었다 — 그때는 8개 장르 중 5개에 대해
     * 사용자가 어떤 의견도 낼 수 없었다("고르지 않음"은 0점일 뿐 "싫다"가 아니다).
     */
    private void addSurveyScore(Map<String, Double> scores, PreferenceSurvey survey) {
        scores.replaceAll((option, previous) -> (double) survey.axisValue(option));
    }

    /**
     * 예매 점수: 관람한 영화 한 편당 정해진 예산을 그 영화의 축에 나눠 담는다.
     * 돈과 시간을 들여 본 작품이라 설문에서 고르지 않은 항목이라도 취향으로 본다.
     * 취소한 예매는 관람하지 않았다는 뜻이라 세지 않는다.
     */
    private void addReservationScore(Map<String, Double> scores, List<Movie> watched, boolean isGenre) {
        for (Movie movie : watched) {
            addMovieScore(scores, movie, isGenre, 1.0);
        }
    }

    /**
     * 평점 점수: 평점은 독립된 점수가 아니라 <b>그 영화의 예매 이력을 얼마나 강하게 반영할지
     * 정하는 배율</b>이다. 그래서 여기서 더하는 값은 배율 전체가 아니라 예매 기여(1.0)와의 차이다.
     * 1점을 준 영화는 배율이 음수라 예매한 사실까지 상쇄하고 취향을 끌어내린다.
     */
    private void addRatingScore(Map<String, Double> scores, List<Movie> watched,
                                Map<Long, Integer> ratingByMovie, boolean isGenre) {
        for (Movie movie : watched) {
            double adjustment = ratingMultiplier(ratingByMovie.get(movie.getId())) - 1.0;
            if (adjustment == 0) continue; // 미평가·3점은 예매만 한 것과 같아 더할 것이 없습니다
            addMovieScore(scores, movie, isGenre, adjustment);
        }
    }

    /**
     * 평점 → 반영 배율.
     * 1.0 이 "예매만 한 상태"의 기준선이다(3점과 미평가가 여기). 위로는 2배까지 키우고
     * 아래로는 기준선 밑으로 내려 취향을 깎는다.
     */
    private double ratingMultiplier(Integer score) {
        if (score == null) return 1.0; // 평점을 남기지 않았으면 예매 이력 그대로
        return switch (score) {
            case 1 -> -0.5; // 예매 기여를 상쇄하고도 남게 깎임
            case 2 -> 0.0;  // 예매한 사실이 없던 것처럼 상쇄
            case 4 -> 1.5;
            case 5 -> 2.0;
            default -> 1.0; // 3점 = 미평가와 동일
        };
    }

    /**
     * 평점 점수: 평점은 독립된 점수가 아니라 그 영화의 예매 이력에 걸리는 <b>배율</b>이다.
     * 그래서 여기서 더하는 값은 배율 전체가 아니라 예매 기여(1.0)와의 차이다.
     */
    private void addMovieScore(Map<String, Double> scores, Movie movie, boolean isGenre, double multiplier) {
        if (movie == null) return;

        List<String> axes = isGenre ? genreAxesOf(movie, scores) : moodAxesOf(movie, scores);
        if (axes.isEmpty()) return; // 나눠 담을 축이 없으면 이 영화는 이 차원에 기여하지 않습니다

        double perAxis = MOVIE_BUDGET * multiplier / axes.size();
        for (String axis : axes) {
            scores.merge(axis, perAxis, Double::sum);
        }
    }

    /** 영화의 장르를 설문 장르명으로 묶고 중복을 제거한 축 목록 */
    private List<String> genreAxesOf(Movie movie, Map<String, Double> scores) {
        Set<String> axes = new LinkedHashSet<>();
        for (String genre : GenreBasedMoodTagger.splitGenres(movie.getGenre())) {
            String canonical = canonicalGenre(genre);
            if (scores.containsKey(canonical)) axes.add(canonical);
        }
        return new ArrayList<>(axes);
    }

    /** 영화에서 두드러지는 분위기 축 목록 */
    private List<String> moodAxesOf(Movie movie, Map<String, Double> scores) {
        List<String> axes = new ArrayList<>();
        moodTagger.tag(movie).forEach((mood, strength) -> {
            if (strength >= MoodTagger.PRESENCE_THRESHOLD && scores.containsKey(mood)) {
                axes.add(mood);
            }
        });
        return axes;
    }

    /**
     * 영화 한 편의 예산을 그 영화가 가진 축에 나눠 담는다 (축마다 만점을 주면 장르가 많이 붙은
     * 영화가 과대평가된다).
     * <b>장르는 반드시 canonicalGenre() 를 통과시킬 것</b> — 맵의 키는 설문 장르명인데 영화 쪽은
     * TMDB 장르명이라 그냥 조회하면 '공포'·'범죄' 같은 값이 전부 빗나간다.
     */
    private int toPercent(double score, double topScore) {
        if (topScore <= 0) return 0;
        return Math.max(0, (int) Math.round(score * 100.0 / topScore));
    }

    private List<CurationResDto.PrefRow> toRows(Map<String, Double> scores, PreferenceSurvey survey, boolean isGenre) {
        double topScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        Map<String, String> icons = isGenre ? GENRE_ICONS : MOOD_ICONS;
        Map<String, String> colors = isGenre ? GENRE_COLORS : MOOD_COLORS;

        List<CurationResDto.PrefRow> rows = new ArrayList<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            String label = entry.getKey();
            double score = entry.getValue();
            rows.add(CurationResDto.PrefRow.builder()
                    .label(label)
                    .percent(toPercent(score, topScore))
                    // 반올림해서 0 이 되는 아주 작은 음수(-0.4 등)까지 기피로 부르면 과하다.
                    // 표시되는 점수와 배지가 어긋나지 않도록 반올림 결과로 판단한다
                    .points((int) Math.round(score))
                    .avoided(Math.round(score) < 0)
                    .rank(rankOf(label, survey, isGenre))
                    .iconClass(icons.getOrDefault(label, "bi bi-stars"))
                    .colorHex(colors.getOrDefault(label, "#ADB5BD"))
                    .build());
        }
        return rows;
    }

    /** 취향 수정 조절판에 뿌릴 축 목록. 값은 마지막으로 저장한 슬라이더 위치 */
    private List<CurationResDto.AxisControl> toControls(List<String> options, PreferenceSurvey survey, boolean isGenre) {
        Map<String, String> icons = isGenre ? GENRE_ICONS : MOOD_ICONS;
        Map<String, String> colors = isGenre ? GENRE_COLORS : MOOD_COLORS;

        List<CurationResDto.AxisControl> controls = new ArrayList<>();
        for (String option : options) {
            controls.add(CurationResDto.AxisControl.builder()
                    .label(option)
                    .value(survey.axisValue(option))
                    .iconClass(icons.getOrDefault(option, "bi bi-stars"))
                    .colorHex(colors.getOrDefault(option, "#ADB5BD"))
                    .build());
        }
        return controls;
    }

    /** 설문에서 몇 순위로 골랐는지. 고르지 않았으면 0 */
    private int rankOf(String label, PreferenceSurvey survey, boolean isGenre) {
        List<String> ranked = isGenre
                ? List.of(survey.getGenreRank1(), survey.getGenreRank2(), survey.getGenreRank3())
                : List.of(survey.getMoodRank1(), survey.getMoodRank2(), survey.getMoodRank3());

        int index = ranked.indexOf(label);
        return index < 0 ? 0 : index + 1;
    }

    private String buildInsightPattern(PreferenceSurvey survey) {
        String first = survey.getGenreRank1();
        String second = survey.getGenreRank2();
        return first + josa(first, "과", "와") + " " + second + josa(second, "을", "를")
                + " 중심으로 한 작품에 높은 만족도를 보이고 있어요.";
    }

    private String buildInsightEmotion(PreferenceSurvey survey) {
        String first = survey.getMoodRank1();
        String second = survey.getMoodRank2();
        return first + " 분위기를 가장 선호하고, " + second + josa(second, "이", "가")
                + " 어우러진 작품에서 특히 만족도가 높아요.";
    }

    /**
     * 앞 단어의 받침 유무로 조사를 고른다 ("액션과 / 드라마와").
     * 한글이 아닌 값(SF 등)은 읽는 소리 기준으로 받침 없는 쪽을 쓴다 ("SF와").
     */
    private String josa(String word, String withFinalConsonant, String withoutFinalConsonant) {
        if (word == null || word.isEmpty()) return withoutFinalConsonant;

        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return withoutFinalConsonant;

        boolean hasFinalConsonant = (last - 0xAC00) % 28 != 0;
        return hasFinalConsonant ? withFinalConsonant : withoutFinalConsonant;
    }

    /**
     * 점수 출처 3칸의 상태. scoreOf() 가 합산하는 출처와 1:1 로 대응한다.
     * 세 출처 모두 구현돼 있으므로 ready=false 는 "아직 쌓인 이력이 없다"는 뜻이다.
     */
    private List<CurationResDto.SourceStatus> buildSources(int reservedMovieCount, int ratedCount) {
        return List.of(
                CurationResDto.SourceStatus.builder()
                        .label("신규 사용자 입력 정보").iconClass("bi bi-clipboard-check")
                        .ready(true).detail("설문에서 선택한 선호 장르/분위기")
                        .build(),
                CurationResDto.SourceStatus.builder()
                        .label("예매 내역").iconClass("bi bi-ticket-perforated")
                        .ready(reservedMovieCount > 0)
                        .detail(reservedMovieCount > 0
                                ? "예매를 확정한 " + reservedMovieCount + "편이 반영되고 있어요"
                                : "예매를 확정하시면 그 영화의 장르/분위기가 쌓여요")
                        .build(),
                CurationResDto.SourceStatus.builder()
                        .label("관람 후 평점").iconClass("bi bi-star")
                        .ready(ratedCount > 0)
                        .detail(ratedCount > 0
                                ? "남기신 평점 " + ratedCount + "건이 예매 이력의 반영 강도를 조절해요"
                                : "평점을 남기시면 그 영화를 얼마나 반영할지 조절돼요")
                        .build()
        );
    }
}
