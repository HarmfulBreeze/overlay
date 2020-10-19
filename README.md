# Overlay
###### An easy-to-use, yet very customizable application to display champion select information in streams
![Gradle CI](https://github.com/piorrro33/overlay/workflows/Gradle%20CI/badge.svg?branch=master "Gradle CI")
![Discord](https://img.shields.io/discord/767052010604199936?color=738ad6&label=Discord&logo=discord&logoColor=white&style=flat "Discord")

![Screenshot](img/overlay_screenshot.png "Screenshot")

## Requirements
- **Windows**
- Java (min. Java 8)

## Getting started
- Download the latest release [here](https://github.com/piorrro33/overlay/releases/latest). Download the win32 archive for 32 bits Java, or the win64 archive for 64 bits Java.
- Extract the zip file and execute ``run.bat`` to start Overlay.

## Customization
Edit the configuration by modifying ``config.conf`` and restarting the application.
You can also customize fonts and more elements: check out ``./web/img/custom`` and ``./web/fonts``.

## Building and running
### Building
- Extract ``./libs/lib/win32.zip`` (for 32 bits Java), ``./libs/lib/win64.zip`` (for 64 bits Java) and ``./web/web.zip``.Select "extract here".
- Open the ``scripts`` folder and run ``build_win32.bat`` or ``build_win64.bat``.
### Running
- Execute ``run_win32.bat`` or ``run_win64.bat`` in the ``scripts`` folder.
