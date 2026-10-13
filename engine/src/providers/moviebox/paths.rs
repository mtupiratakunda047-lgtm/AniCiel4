//! Complete MovieBox API surface from Google Drive base.apk
//! Excludes: money, vip, ad, activity rewards, fission, account login/register

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
pub const CDN_IMAGE: &str = "https://pacdn.aoneroom.com/";

pub mod api {
    pub const APP_CONFIG: &str = "/wefeed-mobile-bff/app/config";
    pub const APP_JS_CONFIG: &str = "/wefeed-mobile-bff/app/js-config";
    pub const CLIENT_LOGS_RETRIEVE_CONFIG: &str = "/wefeed-mobile-bff/client_logs_retrieve/config";
    pub const COMMENT: &str = "/wefeed-mobile-bff/comment";
    pub const COMMENT_LIKE: &str = "/wefeed-mobile-bff/comment/like";
    pub const COMMENT_LIST: &str = "/wefeed-mobile-bff/comment/list";
    pub const FEEDBACK_COMMIT: &str = "/wefeed-mobile-bff/feedback/commit";
    pub const FEEDBACK_LABEL_LIST: &str = "/wefeed-mobile-bff/feedback/label/list";
    pub const FEEDBACK_REPORT: &str = "/wefeed-mobile-bff/feedback/report";
    pub const GROUP_CREATE: &str = "/wefeed-mobile-bff/group/create";
    pub const GROUP_EXIST: &str = "/wefeed-mobile-bff/group/exist";
    pub const GROUP_GET: &str = "/wefeed-mobile-bff/group/get";
    pub const GROUP_JOIN: &str = "/wefeed-mobile-bff/group/join";
    pub const GROUP_LEAVE: &str = "/wefeed-mobile-bff/group/leave";
    pub const GROUP_LIST_SEARCH: &str = "/wefeed-mobile-bff/group/list/search";
    pub const GROUP_LIST_SELECT: &str = "/wefeed-mobile-bff/group/list/select";
    pub const GROUP_LIST_USER: &str = "/wefeed-mobile-bff/group/list/user";
    pub const GROUP_RANK: &str = "/wefeed-mobile-bff/group/rank";
    pub const GROUP_UPDATE: &str = "/wefeed-mobile-bff/group/update";
    pub const INTERACTIVE_POST_LIKE: &str = "/wefeed-mobile-bff/interactive/post/like";
    pub const LEARNING_ADD_COURSE: &str = "/wefeed-mobile-bff/learning/add-course";
    pub const LEARNING_MY_COURSE: &str = "/wefeed-mobile-bff/learning/my-course";
    pub const LEARNING_PREFER_OPTIONS: &str = "/wefeed-mobile-bff/learning/prefer-options";
    pub const LEARNING_SUBMIT_PREFER: &str = "/wefeed-mobile-bff/learning/submit-prefer";
    pub const MESSAGE_INTERNAL_HAS_NEW: &str = "/wefeed-mobile-bff/message/internal/has-new";
    pub const MESSAGE_INTERNAL_LIST: &str = "/wefeed-mobile-bff/message/internal/list";
    pub const MESSAGE_PUSH_LOCAL_LIST: &str = "/wefeed-mobile-bff/message/push/local/list";
    pub const MESSAGE_REPORT: &str = "/wefeed-mobile-bff/message/report";
    pub const PLAYLIST_CONTENT: &str = "/wefeed-mobile-bff/playlist/content";
    pub const POST_CREATE: &str = "/wefeed-mobile-bff/post/create";
    pub const POST_DELETE: &str = "/wefeed-mobile-bff/post/delete";
    pub const POST_GET: &str = "/wefeed-mobile-bff/post/get";
    pub const POST_LIST_CORRELATION: &str = "/wefeed-mobile-bff/post/list/correlation";
    pub const POST_LIST_GROUP: &str = "/wefeed-mobile-bff/post/list/group";
    pub const POST_LIST_IMMERSIVE: &str = "/wefeed-mobile-bff/post/list/immersive";
    pub const POST_LIST_LIKES: &str = "/wefeed-mobile-bff/post/list/likes";
    pub const POST_LIST_SUBJECT: &str = "/wefeed-mobile-bff/post/list/subject";
    pub const POST_LIST_USER: &str = "/wefeed-mobile-bff/post/list/user";
    pub const POST_LIST_USER_MY: &str = "/wefeed-mobile-bff/post/list/user/my";
    pub const RETRIEVE_CLIENT_LOGS_REPORT: &str = "/wefeed-mobile-bff/retrieve_client_logs/report";
    pub const SEARCH_ANAYLZE_SEEK: &str = "/wefeed-mobile-bff/search-anaylze/seek";
    pub const SHARE_LONGURL: &str = "/wefeed-mobile-bff/share/longurl";
    pub const SHARE_SHORTURL: &str = "/wefeed-mobile-bff/share/shorturl";
    pub const SHORTS_FAVORITE: &str = "/wefeed-mobile-bff/shorts/favorite";
    pub const SHORTS_FAVORITE_LIST: &str = "/wefeed-mobile-bff/shorts/favorite-list";
    pub const SHORTS_GET_INFO: &str = "/wefeed-mobile-bff/shorts/get-info";
    pub const SHORTS_MINI_LIST: &str = "/wefeed-mobile-bff/shorts/mini-list";
    pub const SHORTS_MOST_TRENDING: &str = "/wefeed-mobile-bff/shorts/most-trending";
    pub const SHORTS_OPERATING: &str = "/wefeed-mobile-bff/shorts/operating";
    pub const SHORTS_REEL: &str = "/wefeed-mobile-bff/shorts/reel";
    pub const SNIFF_CONFIG: &str = "/wefeed-mobile-bff/sniff/config";
    pub const STATISTICS_USER_OPERATION: &str = "/wefeed-mobile-bff/statistics/user-operation";
    pub const SUBJECT_API_BOTTOM_TAB: &str = "/wefeed-mobile-bff/subject-api/bottom-tab";
    pub const SUBJECT_API_CANCEL_HAVE_SEEN: &str = "/wefeed-mobile-bff/subject-api/cancel-have-seen";
    pub const SUBJECT_API_DAILY_MOVIE_REC: &str = "/wefeed-mobile-bff/subject-api/daily-movie-rec";
    pub const SUBJECT_API_DETAIL_REC: &str = "/wefeed-mobile-bff/subject-api/detail-rec";
    pub const SUBJECT_API_DUB_INFO: &str = "/wefeed-mobile-bff/subject-api/dub-info";
    pub const SUBJECT_API_FILTER_ITEMS: &str = "/wefeed-mobile-bff/subject-api/filter-items";
    pub const SUBJECT_API_FINISH_DOWNLOAD_RESOURCE: &str = "/wefeed-mobile-bff/subject-api/finish-download-resource";
    pub const SUBJECT_API_GET: &str = "/wefeed-mobile-bff/subject-api/get";
    pub const SUBJECT_API_GET_EXT_CAPTIONS: &str = "/wefeed-mobile-bff/subject-api/get-ext-captions";
    pub const SUBJECT_API_GET_STREAM_CAPTIONS: &str = "/wefeed-mobile-bff/subject-api/get-stream-captions";
    pub const SUBJECT_API_HAVE_SEEN: &str = "/wefeed-mobile-bff/subject-api/have-seen";
    pub const SUBJECT_API_LIST: &str = "/wefeed-mobile-bff/subject-api/list";
    pub const SUBJECT_API_PLAY_INFO: &str = "/wefeed-mobile-bff/subject-api/play-info";
    pub const SUBJECT_API_PLAY_RELATED_REC: &str = "/wefeed-mobile-bff/subject-api/play-related-rec";
    pub const SUBJECT_API_RESOURCE: &str = "/wefeed-mobile-bff/subject-api/resource";
    pub const SUBJECT_API_RESOURCE_POSITION: &str = "/wefeed-mobile-bff/subject-api/resource-position";
    pub const SUBJECT_API_SEARCH: &str = "/wefeed-mobile-bff/subject-api/search";
    pub const SUBJECT_API_SEARCH_RANK: &str = "/wefeed-mobile-bff/subject-api/search-rank";
    pub const SUBJECT_API_SEARCH_RANK_V2: &str = "/wefeed-mobile-bff/subject-api/search-rank/v2";
    pub const SUBJECT_API_SEARCH_SUGGEST: &str = "/wefeed-mobile-bff/subject-api/search-suggest";
    pub const SUBJECT_API_SEASON_INFO: &str = "/wefeed-mobile-bff/subject-api/season-info";
    pub const SUBJECT_API_SEE_LIST_V2: &str = "/wefeed-mobile-bff/subject-api/see-list-v2";
    pub const SUBJECT_API_STAFF_INFO: &str = "/wefeed-mobile-bff/subject-api/staff-info";
    pub const SUBJECT_API_STAFF_RELATED: &str = "/wefeed-mobile-bff/subject-api/staff-related";
    pub const SUBJECT_API_STAFF_SUBJECT_LIST: &str = "/wefeed-mobile-bff/subject-api/staff-subject-list";
    pub const SUBJECT_API_START_DOWNLOAD_RESOURCE: &str = "/wefeed-mobile-bff/subject-api/start-download-resource";
    pub const SUBJECT_API_SUBTITLE_SEARCH: &str = "/wefeed-mobile-bff/subject-api/subtitle-search";
    pub const SUBJECT_API_TOP_REC: &str = "/wefeed-mobile-bff/subject-api/top-rec";
    pub const SUBJECT_API_TRENDING_V2: &str = "/wefeed-mobile-bff/subject-api/trending/v2";
    pub const SUBJECT_API_WANT_TO_SEE: &str = "/wefeed-mobile-bff/subject-api/want-to-see";
    pub const SUBJECT_API_WANT_TO_SEE_STAFF: &str = "/wefeed-mobile-bff/subject-api/want-to-see-staff";
    pub const TAB_OPERATING: &str = "/wefeed-mobile-bff/tab-operating";
    pub const TAB_RANKING_LIST: &str = "/wefeed-mobile-bff/tab/ranking-list";
    pub const USER_API_BLOCK: &str = "/wefeed-mobile-bff/user-api/block";
    pub const USER_API_INFO: &str = "/wefeed-mobile-bff/user-api/info";
    pub const USER_API_LOGOUT: &str = "/wefeed-mobile-bff/user-api/logout";
    pub const USER_API_MODIFY: &str = "/wefeed-mobile-bff/user-api/modify";
    pub const USER_API_PROFILE: &str = "/wefeed-mobile-bff/user-api/profile";
    pub const USER_API_UNBLOCK: &str = "/wefeed-mobile-bff/user-api/unblock";
}

/// Core streaming subset for quick imports
pub mod subject {
    pub const GET: &str = "/wefeed-mobile-bff/subject-api/get";
    pub const PLAY_INFO: &str = "/wefeed-mobile-bff/subject-api/play-info";
    pub const RESOURCE: &str = "/wefeed-mobile-bff/subject-api/resource";
    pub const SEASON_INFO: &str = "/wefeed-mobile-bff/subject-api/season-info";
    pub const SEARCH: &str = "/wefeed-mobile-bff/subject-api/search";
    pub const SEARCH_RANK_V2: &str = "/wefeed-mobile-bff/subject-api/search-rank/v2";
    pub const SEARCH_SUGGEST: &str = "/wefeed-mobile-bff/subject-api/search-suggest";
    pub const TRENDING_V2: &str = "/wefeed-mobile-bff/subject-api/trending/v2";
    pub const TOP_REC: &str = "/wefeed-mobile-bff/subject-api/top-rec";
    pub const DETAIL_REC: &str = "/wefeed-mobile-bff/subject-api/detail-rec";
    pub const DAILY_MOVIE_REC: &str = "/wefeed-mobile-bff/subject-api/daily-movie-rec";
    pub const EXT_CAPTIONS: &str = "/wefeed-mobile-bff/subject-api/get-ext-captions";
    pub const STREAM_CAPTIONS: &str = "/wefeed-mobile-bff/subject-api/get-stream-captions";
    pub const START_DOWNLOAD: &str = "/wefeed-mobile-bff/subject-api/start-download-resource";
    pub const FINISH_DOWNLOAD: &str = "/wefeed-mobile-bff/subject-api/finish-download-resource";
    pub const TAB_OPERATING: &str = "/wefeed-mobile-bff/tab-operating";
    pub const BOTTOM_TAB: &str = "/wefeed-mobile-bff/subject-api/bottom-tab";
    pub const FILTER_ITEMS: &str = "/wefeed-mobile-bff/subject-api/filter-items";
}
