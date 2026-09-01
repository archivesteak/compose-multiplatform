// see https://kotlinlang.org/docs/js-project-setup.html#webpack-configuration-file
// This file provides karma.config.d configuration to run tests with k/wasm and k/js.
//
// The whole body is wrapped in an IIFE so local declarations do not leak into the
// generated karma.conf.js scope or collide with other configuration fragments.
(function (config) {
    const fs = require("fs");
    const path = require("path");

    config.browserConsoleLogOptions.level = "debug";

    const basePath = config.basePath;
    const projectPath = path.resolve(basePath, "..", "..", "..", "..");
    const generatedAssetsPath = path.resolve(projectPath, "build", "karma-webpack-out")

    const debug = message => console.log(`[karma-config] ${message}`);

    debug(`karma basePath: ${basePath}`);
    debug(`karma generatedAssetsPath: ${generatedAssetsPath}`);

    const kotlinPath = path.resolve(basePath, "kotlin");

    // Karma exposes files below its base path at /base/. ResourceReader fetches
    // test assets from the web root, so route those requests to the served
    // Kotlin package instead of treating a local filesystem path as an HTTP
    // proxy target.
    config.proxies["/"] = "/base/kotlin/";

    const skikoReexports = path.resolve(kotlinPath, "js-skiko-reexport-symbols.mjs");
    const skikoModule = path.resolve(kotlinPath, "skiko.mjs");
    const skikoWasm = path.resolve(kotlinPath, "skiko.wasm");
    const skikoLoader = path.resolve(kotlinPath, "compose-skiko-loader.js");
    const skikoFiles = [];

    if (fs.existsSync(skikoReexports)) {
        fs.writeFileSync(skikoLoader, `
(function () {
    if (!window.__karma__) return;
    const originalLoaded = window.__karma__.loaded.bind(window.__karma__);
    let skikoReady;
    window.__karma__.loaded = function () {
        if (!skikoReady) {
            const reexportUrl = Object.keys(window.__karma__.files || {})
                .find((url) => url.endsWith("js-skiko-reexport-symbols.mjs"));
            skikoReady = reexportUrl
                ? import(reexportUrl).then((module) => module.api.awaitSkiko)
                : Promise.reject(new Error("Skiko re-export module was not served by Karma"));
        }
        skikoReady.then(originalLoaded).catch((error) => {
            window.__karma__.error(error && error.stack ? error.stack : String(error));
        });
    };
})();
`.trim());

        skikoFiles.push(
            skikoLoader,
            {pattern: skikoReexports, included: false, served: true, watched: false},
            {pattern: skikoModule, included: false, served: true, watched: false},
            {pattern: skikoWasm, included: false, served: true, watched: false},
        );
    }

    config.files = skikoFiles.concat([
        {pattern: path.resolve(generatedAssetsPath, "**/*"), included: false, served: true, watched: false},
        {pattern: path.resolve(kotlinPath, "**/*.png"), included: false, served: true, watched: false},
        {pattern: path.resolve(kotlinPath, "**/*.cvr"), included: false, served: true, watched: false},
        {pattern: path.resolve(kotlinPath, "**/*.otf"), included: false, served: true, watched: false},
        {pattern: path.resolve(kotlinPath, "**/*.gif"), included: false, served: true, watched: false},
        {pattern: path.resolve(kotlinPath, "**/*.ttf"), included: false, served: true, watched: false},
        {pattern: path.resolve(kotlinPath, "**/*.txt"), included: false, served: true, watched: false},
        {pattern: path.resolve(kotlinPath, "**/*.json"), included: false, served: true, watched: false},
        {pattern: path.resolve(kotlinPath, "**/*.xml"), included: false, served: true, watched: false},
    ], config.files);

    function KarmaWebpackOutputFramework(config) {
        // This controller is instantiated and set during the preprocessor phase.
        const controller = config.__karmaWebpackController;

        // only if webpack has instantiated its controller
        if (!controller) {
            console.warn(
                "Webpack has not instantiated controller yet.\n" +
                "Check if you have enabled webpack preprocessor and framework before this framework"
            )
            return
        }

        config.files.push({
            pattern: `${controller.outputPath}/**/*`,
            included: false,
            served: true,
            watched: false
        })
    }

    const KarmaWebpackOutputPlugin = {
        'framework:webpack-output': ['factory', KarmaWebpackOutputFramework],
    };

    config.plugins.push(KarmaWebpackOutputPlugin);
    config.frameworks.push("webpack-output");
})(config);
