# Application.mk for Delos

# The same as minSdk in app/build.gradle (Android 10)
APP_PLATFORM := android-29

# C++ for the PyroWave codec and renderer
APP_STL := c++_static

# We support 16KB pages
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
