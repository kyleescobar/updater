# Updater
A fork of sfPlayer1's (https://github.com/sfplayer1) matcher with that dynamically updates Old School RuneScape deobfuscated gamepack jars.

It allows you to decompile a deob in one revision and refactor it in IntelliJ like normal. When a new version comes out, simply deobfuscate it with the same settings as you previously did, and then pass the new and refactored deob jars into this program.
It will spit out the new jar version with all of your names from previous one.

If you need a deobfuscator for OSRS, you can use mine. Its updated as of revision 213.
https://github.com/runebox-project/revtools

## Added Features
* Reworked a lot of his ASM backend to support static nodes and decouple them from being considered hierarchy members.
* Added the ability to directly export a mapped JAR from matches.
* Reworked the classifiers for static members to not include hierarchy based checks
* Added support for inlined static methods between inputs so matching score isn't impacted.
* Added customizable themes via CSS.
* Reworked the parallel task dispatching to utilize kotlinx coroutine flows.

## Showcase
![](https://i.imgur.com/b6l56FI.gif)
