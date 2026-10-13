//! MovieBox paths extracted from Google Drive base.apk only.
//! No Netplay / no VIP / no money / no ads endpoints.

pub const HOST_POOL: &[&str] = &[
    "https://api.inmoviebox.com",
    "https://api.aoneroom.com",
    "https://api6.aoneroom.com",
    "https://api5.aoneroom.com",
    "https://api4.aoneroom.com",
    "https://api4sg.aoneroom.com",
    "https://api3.aoneroom.com",
    "https://api6sg.aoneroom.com",
];

pub const STREAM_REFERER: &str = "https://h5.aoneroom.com/";

pub mod subject {
    pub const GET: &str = "/wefeed-mobile-bff/subject-api/get";
    pub const PLAY_INFO: &str = "/wefeed-mobile-bff/subject-api/play-info";
    pub const RESOURCE: &str = "/wefeed-mobile-bff/subject-api/resource";
    pub const SEASON_INFO: &str = "/wefeed-mobile-bff/subject-api/season-info";
    pub const SEARCH: &str = "/wefeed-mobile-bff/subject-api/search";
    pub const SEARCH_RANK: &str = "/wefeed-mobile-bff/subject-api/search-rank";
    pub const SEARCH_RANK_V2: &str = "/wefeed-mobile-bff/subject-api/search-rank/v2";
    pub const SEARCH_SUGGEST: &str = "/wefeed-mobile-bff/subject-api/search-suggest";
    pub const TRENDING_V2: &str = "/wefeed-mobile-bff/subject-api/trending/v2";
    pub const TOP_REC: &str = "/wefeed-mobile-bff/subject-api/top-rec";
    pub const DETAIL_REC: &str = "/wefeed-mobile-bff/subject-api/detail-rec";
    pub const DAILY_MOVIE_REC: &str = "/wefeed-mobile-bff/subject-api/daily-movie-rec";
    pub const PLAY_RELATED_REC: &str = "/wefeed-mobile-bff/subject-api/play-related-rec";
    pub const SEE_LIST_V2: &str = "/wefeed-mobile-bff/subject-api/see-list-v2";
    pub const WANT_TO_SEE: &str = "/wefeed-mobile-bff/subject-api/want-to-see";
    pub const LIST: &str = "/wefeed-mobile-bff/subject-api/list";
    pub const FILTER_ITEMS: &str = "/wefeed-mobile-bff/subject-api/filter-items";
    pub const BOTTOM_TAB: &str = "/wefeed-mobile-bff/subject-api/bottom-tab";
    pub const EXT_CAPTIONS: &str = "/wefeed-mobile-bff/subject-api/get-ext-captions";
    pub const STREAM_CAPTIONS: &str = "/wefeed-mobile-bff/subject-api/get-stream-captions";
    pub const START_DOWNLOAD: &str = "/wefeed-mobile-bff/subject-api/start-download-resource";
    pub const FINISH_DOWNLOAD: &str = "/wefeed-mobile-bff/subject-api/finish-download-resource";
    pub const TAB_OPERATING: &str = "/wefeed-mobile-bff/tab-operating";
}
