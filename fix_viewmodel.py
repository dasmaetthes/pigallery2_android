import re

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'r') as f:
    content = f.read()

connect_old = """    fun connectAndLogin(url: String, user: String, pass: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = LoginUiState.Loading
            try {
                // Ensure URL has schema
                val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "http://$url"
                } else {
                    url
                }

                val (cookies, apiPrefix) = api.login(formattedUrl, user, pass)"""

connect_new = """    fun connectAndLogin(url: String, user: String, pass: String, allowInsecureSsl: Boolean = prefs.allowInsecureSsl) {
        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = LoginUiState.Loading
            try {
                // Ensure URL has schema
                val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "http://$url"
                } else {
                    url
                }

                prefs.allowInsecureSsl = allowInsecureSsl
                api.updateClientSsl(allowInsecureSsl)

                val (cookies, apiPrefix) = api.login(formattedUrl, user, pass)"""

if connect_old in content:
    content = content.replace(connect_old, connect_new)
    print("Replaced connectAndLogin")
else:
    print("Could not find connectAndLogin")

with open('app/src/main/java/com/example/ui/GalleryViewModel.kt', 'w') as f:
    f.write(content)
