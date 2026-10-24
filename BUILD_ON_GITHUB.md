# Jar banano (free, GitHub Actions diye)

`gradle-wrapper.jar` ei source-e nai, tai `./gradlew` kaj korbe na. Tai GitHub theke build korun:

1. github.com-e free account banan, notun repository (`crystalrush`) create korun.
2. Ei project-er shob file (hidden `.github` folder shoho) repository-te upload korun.
3. **Actions** tab > **Build CrystalRush** > build shesh hobe (kichu minute).
4. Build-er page-er niche **Artifacts** theke `crystalrush-jar` download korun. Ete `crystalrush-1.0.0+1.21.11.jar` thakbe.
5. Jar ta `.minecraft/mods/` e rakhun (Fabric Loader + Fabric API 0.141.4+1.21.11 lagbe).

PC-te build korte chaile: JDK 21 + Gradle 8.12 install kore `gradle build` chalan.
