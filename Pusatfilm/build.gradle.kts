// use an integer for version numbers
version = 2

cloudstream {
    // Tambahkan baris ini agar nama plugin jelas
    name = "Pusatfilm" 
    
    description = "Pusatfilm is a plugin that provides streaming links for movies and TV series."
    language = "id"
    authors = listOf("Hexated", "AdiMovies")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=gomov.bio&sz=%size%"
}
