# PiGallery2 Android Client

A modern and native Android companion application designed specifically for browsing and viewing media hosted on a [PiGallery2](https://github.com/bpatrik/pigallery2) server. 

This client delivers a fluid and beautifully designed user interface, allowing you to easily browse your directories, query saved albums, view recognized faces, and rediscover your memories with ease.

<img src="https://github.com/dasmaetthes/pigallery2_android/blob/main/.github/images/pigallery_1.png?raw=true" alt="drawing" width="200"/> <img src="https://github.com/dasmaetthes/pigallery2_android/blob/main/.github/images/pigallery_2.png?raw=true" alt="drawing" width="200"/> <img src="https://github.com/dasmaetthes/pigallery2_android/blob/main/.github/images/pigallery_3.png?raw=true" alt="drawing" width="200"/> <img src="https://github.com/dasmaetthes/pigallery2_android/blob/main/.github/images/pigallery_4.png?raw=true" alt="drawing" width="200"/>

---

## 🌟 Key Features

- **Folder and directory browsing:** Securely connect to your PiGallery2 server and browse your photo and video collection within its original directory structure.
- **Advanced Query Builder:** Use the visual query builder to create complex searches and filter media by keywords, date ranges, file types and other advanced metadata parameters.
- **Interactive Map View:** Explore geotagged photos and videos on an interactive map.
	- Easily view the locations where your memories were captured.
	- Please note that to ensure a smooth, TV-friendly interface, interactive maps are automatically disabled when running on Android TV.
- **Android TV Optimised:** Full support for all Android TV features.
    - Optimised layouts and robust D-pad navigation support.
    - Performance-heavy map views are disabled on Android TV to keep the focus on smooth media presentation and navigation.
- **Customisable layouts and grid control:** Adjust the number of columns, image aspect ratios, grid item spacing and corner roundedness.
- Rich Media Viewer: Intuitive zoom-to-span and panning gestures for high-resolution images.
    - Face recognition overlays highlight detected people in images.
	-  Share images instantly via messenger 
- **Comprehensive Metadata Explorer:** A metadata panel displaying full EXIF details, including:
	- camera model and lens type
	- ISO, exposure, focal length and aperture
    - Keywords and detected faces
- **Albums:** Seamless access to saved albums created within the PiGallery2 web interface with customisable item sorting.
- **People & Face Recognition:** Browse a structured grid of recognised people.
	- Manage and mark favourite people.
	- Please note that it does not perform face recognition independently.
- **Rediscover:** Revisit a randomly selected moments from your library.

---

## 🛠️ Built with Open Source Software

This application leverages modern Android libraries:

- **Jetpack Compose & Material 3** — Modern declarative UI toolkit with adaptive components and dynamic theming.
- **Kotlin & Kotlin Coroutines** — Type-safe, expressive programming language with robust asynchronous state management.
- **Retrofit & OkHttp** — Fast, type-safe REST client for efficient, cookie-persistent communications with the PiGallery2 API.
- **Moshi** — Modern JSON library for Kotlin and Java to parse complex API metadata.
- **Coil Image Loader** — Fast, memory-efficient image loading library designed specifically for Jetpack Compose.

---

## 💡 Inspiration & Credits

- **Inspired by:** **[pigallery2_android_test](https://github.com/Lakjdf/pigallery2_android_test)** by Lakjdf.
- **Developed with AI:** This application was developed and optimized with the assistance of advanced AI models.
