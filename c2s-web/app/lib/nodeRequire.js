import { createRequire } from "node:module";

// Loads a CommonJS module from an absolute path at request time, outside the Next bundle.
// webpack rewrites createRequire into its bundle require, which cannot load a sibling repo's
// script by absolute path; __non_webpack_require__ is the real Node require when bundled.
export const nodeRequire = typeof __non_webpack_require__ === "function"
  ? __non_webpack_require__
  : createRequire(import.meta.url);
