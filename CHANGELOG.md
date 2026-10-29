# Changelog

## [2.0.0](https://github.com/PlayFoundryHQ/AvaCore/compare/v1.3.0...v2.0.0) (2026-10-29)


### ⚠ BREAKING CHANGES

* AvaCore no longer speaks English or Swedish; apps asking for those languages get LANG_NOT_SUPPORTED and fall back to another engine.

### Features

* Persian-only engine, spoken (Goftari) Persian by default ([fd2b02e](https://github.com/PlayFoundryHQ/AvaCore/commit/fd2b02e0b3490c95b33e5887bff7eda4ed2726ac))

## [1.3.0](https://github.com/PlayFoundryHQ/AvaCore/compare/v1.2.0...v1.3.0) (2026-10-29)


### Features

* fix the Persian words eSpeak reads with wrong vowels ([c535adf](https://github.com/PlayFoundryHQ/AvaCore/commit/c535adf30cb1cf98fee53e9ffd7727f2b3d20e20))
* Goftari mode — read Persian the way it is spoken ([a7cc2b3](https://github.com/PlayFoundryHQ/AvaCore/commit/a7cc2b3855d9d8fb4ebfbb626687c95fd3d1d74e))
* natural pauses for verse, ellipses, dashes and lists ([2e9c744](https://github.com/PlayFoundryHQ/AvaCore/commit/2e9c7442c97438cb814ec0746bfbb1b9554880c1))

## [1.2.0](https://github.com/PlayFoundryHQ/AvaCore/compare/v1.1.0...v1.2.0) (2026-10-24)


### Features

* expand Persian lexicon; fix docs/landing page drift ([#11](https://github.com/PlayFoundryHQ/AvaCore/issues/11)) ([5ac77fe](https://github.com/PlayFoundryHQ/AvaCore/commit/5ac77fe97565db8dfaf695fd84f537d4cb816790))
* in-app voice test screen + NNAPI trial with CPU fallback (Phase 2 [#6](https://github.com/PlayFoundryHQ/AvaCore/issues/6), [#7](https://github.com/PlayFoundryHQ/AvaCore/issues/7)) ([#8](https://github.com/PlayFoundryHQ/AvaCore/issues/8)) ([fc6e045](https://github.com/PlayFoundryHQ/AvaCore/commit/fc6e045c28e3d5e5d281dd0035f7ab6bc366f40e))
* multi-language engine — English and Swedish alongside Persian ([#5](https://github.com/PlayFoundryHQ/AvaCore/issues/5)) ([bc482c0](https://github.com/PlayFoundryHQ/AvaCore/commit/bc482c0fdbaf23238eb237b8f978cb48e698e3b1))
* on-demand model download for English/Swedish (Phase 2 [#5](https://github.com/PlayFoundryHQ/AvaCore/issues/5)) ([#9](https://github.com/PlayFoundryHQ/AvaCore/issues/9)) ([7fe8efb](https://github.com/PlayFoundryHQ/AvaCore/commit/7fe8efb2acc64b215a01490e8118d0d27f790e9c))
* richer normalizer + SSML prosody/emphasis (Phase 3 [#9](https://github.com/PlayFoundryHQ/AvaCore/issues/9), [#10](https://github.com/PlayFoundryHQ/AvaCore/issues/10)) ([#10](https://github.com/PlayFoundryHQ/AvaCore/issues/10)) ([8033d1d](https://github.com/PlayFoundryHQ/AvaCore/commit/8033d1d3b737e4887fef2cae57b44e84b0234ac4))


### Bug Fixes

* implement onIsValidVoiceName/onLoadVoice — setVoice() was silently failing ([#6](https://github.com/PlayFoundryHQ/AvaCore/issues/6)) ([159536a](https://github.com/PlayFoundryHQ/AvaCore/commit/159536a81247a3e253f07803726590196bc77732))

## [1.1.0](https://github.com/OpScaleHub/AvaCore/compare/v1.0.0...v1.1.0) (2026-06-30)


### Features

* initial production release — offline Persian TTS with signed APK pipeline ([928852e](https://github.com/OpScaleHub/AvaCore/commit/928852e14a4e7c3797eb804cd12d87a632b4b008))
