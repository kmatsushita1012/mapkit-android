(function () {
  const state = {
    token: null,
    mapReady: false,
    map: null,
    region: {
      centerLat: 35.681236,
      centerLng: 139.767125,
      latDelta: 0.05,
      lngDelta: 0.05,
    },
    mapStyle: "standard",
    appearance: "auto",
    language: "auto",
    showsCompass: true,
    showsScale: false,
    showsZoomControl: true,
    showsMapTypeControl: false,
    showsPointsOfInterest: true,
    poiFilter: { type: "MKPoiFilterAll", categories: [] },
    isRotateEnabled: true,
    isScrollEnabled: true,
    isZoomEnabled: true,
    isPitchEnabled: true,
    annotations: [],
    overlays: [],
    userLocationEnabled: false,
    userLocationFollowsHeading: false,
    userLocationShowsAccuracyRing: true,
    userLocationWatchId: null,
    annotationsById: {},
    annotationElementsById: {},
    overlaysById: {},
    annotationHashesById: {},
    overlayHashesById: {},
    lastSelectedAnnotationId: null,
    pendingDomTapSelectionId: null,
    longPressHandlersInstalled: false,
    suppressNextRegionWillChange: true,
  };

  function emit(payload) {
    if (window.AndroidMKBridge && window.AndroidMKBridge.emitEvent) {
      window.AndroidMKBridge.emitEvent(JSON.stringify(payload));
    }
  }

  function debugLog(message) {
    // keep silent in normal operation to avoid noisy console output
  }

  function debugError(message) {
    try {
      if (typeof console !== "undefined" && typeof console.error === "function") {
        console.error("[MKBridge] " + String(message));
      } else {
        debugLog("ERROR " + String(message));
      }
    } catch (_) {}
  }

  function installGlobalErrorHandlers() {
    if (typeof window === "undefined") return;
    if (window.__mkBridgeGlobalErrorInstalled) return;
    window.__mkBridgeGlobalErrorInstalled = true;

    try {
      window.addEventListener("error", function (event) {
        const message = event && event.message ? event.message : "unknown js error";
        const file = event && event.filename ? event.filename : "unknown";
        const line = event && event.lineno ? event.lineno : 0;
        const col = event && event.colno ? event.colno : 0;
        debugError("window.error message=" + message + " at " + file + ":" + line + ":" + col);
      });
    } catch (_) {}

    try {
      window.addEventListener("unhandledrejection", function (event) {
        const reason = event && typeof event.reason !== "undefined"
          ? String(event.reason)
          : "unknown rejection";
        debugError("window.unhandledrejection reason=" + reason);
      });
    } catch (_) {}

    debugLog("global error handlers installed");
  }

  installGlobalErrorHandlers();

  function emitBridgeError(message) {
    debugError("bridgeError " + String(message || "unknown bridge error"));
    emit({
      type: "bridgeError",
      message: String(message || "unknown bridge error"),
    });
  }

  function renderStatus() {
    const status = document.getElementById("status");
    if (!status) return;
    status.textContent =
      "mode: " + (state.mapReady ? "mapkit" : "not-ready") + "\n" +
      "center: " + state.region.centerLat.toFixed(6) + ", " + state.region.centerLng.toFixed(6) + "\n" +
      "span: " + state.region.latDelta.toFixed(5) + ", " + state.region.lngDelta.toFixed(5);
  }

  function stableHash(value) {
    return JSON.stringify(value);
  }

  function stableAnnotationHash(item) {
    if (!item || typeof item !== "object") return stableHash(item);
    const normalized = Object.assign({}, item);
    delete normalized.isSelected;
    return stableHash(normalized);
  }

  function domAnnotationStructureHash(item) {
    const style = item && item.style ? item.style : {};
    return stableHash({
      id: item && item.id,
      lat: item && item.lat,
      lng: item && item.lng,
      isVisible: item && item.isVisible,
      isDraggable: item && item.isDraggable,
      kind: style.kind,
      anchorX: style.anchorX,
      anchorY: style.anchorY,
      allowSelection: style.allowSelection,
    });
  }

  function domAnnotationContentHash(item) {
    const style = item && item.style ? item.style : {};
    return stableHash({
      html: style.html,
    });
  }

  function sanitizeDomAnnotationHtml(html) {
    const value = String(html || "");
    if (/<\s*script\b/i.test(value)) return null;
    if (/\son[a-z]+\s*=/i.test(value)) return null;
    return value;
  }

  function updateDomAnnotationContent(id, html) {
    const sanitized = sanitizeDomAnnotationHtml(html);
    if (sanitized == null) {
      emitBridgeError("Rejected unsafe HTML for annotation id=" + String(id));
      return false;
    }
    const element = state.annotationElementsById[String(id)];
    if (!element) return false;
    element.innerHTML = sanitized;
    return true;
  }

  function mapTypeFor(style) {
    if (!window.mapkit || !window.mapkit.Map || !window.mapkit.Map.MapTypes) return null;
    const types = window.mapkit.Map.MapTypes;
    switch (style) {
      case "mutedStandard":
        return types.MutedStandard || types.Standard;
      case "satellite":
        return types.Satellite;
      case "hybrid":
        return types.Hybrid;
      case "standard":
      default:
        return types.Standard;
    }
  }

  function colorSchemeFor(appearance) {
    if (!window.mapkit || !window.mapkit.Map || !window.mapkit.Map.ColorSchemes) return null;
    const schemes = window.mapkit.Map.ColorSchemes;
    switch (appearance) {
      case "dark":
        return schemes.Dark;
      case "light":
        return schemes.Light;
      case "auto":
      default:
        return schemes.Adaptive || null;
    }
  }

  function featureVisibilityFor(enabled) {
    if (!window.mapkit || !window.mapkit.FeatureVisibility) return enabled;
    return enabled
      ? (window.mapkit.FeatureVisibility.Visible || window.mapkit.FeatureVisibility.Adaptive)
      : (window.mapkit.FeatureVisibility.Hidden || window.mapkit.FeatureVisibility.Adaptive);
  }

  function toPoiCategory(category) {
    if (!window.mapkit || !window.mapkit.PointOfInterestCategory) return null;
    const c = window.mapkit.PointOfInterestCategory;
    if (typeof category !== "string") return null;
    if (c[category]) return c[category];
    const normalized = category.replace(/[-_ ]/g, "").toLowerCase();
    const matchedKey = Object.keys(c).find((k) => (
      typeof k === "string" &&
      k.replace(/[-_ ]/g, "").toLowerCase() === normalized
    ));
    return matchedKey ? c[matchedKey] : null;
  }

  function categoriesFromFilter(filter) {
    const src = (filter && filter.categories) ? filter.categories : [];
    return src
      .map((v) => toPoiCategory(v))
      .filter((v) => !!v);
  }

  function applyPoiFilter(filter, defaultPoiVisible) {
    if (!state.mapReady || !state.map) return;
    const effectiveFilter = filter || { type: "MKPoiFilterAll", categories: [] };
    const rawType = String(effectiveFilter.type || "MKPoiFilterAll");
    const isAll = rawType === "MKPoiFilterAll" || rawType.toLowerCase() === "all";
    const isNone = rawType === "MKPoiFilterNone" || rawType.toLowerCase() === "none";
    const isInclude = rawType === "MKPoiFilterInclude" || rawType.toLowerCase() === "include";
    const isExclude = rawType === "MKPoiFilterExclude" || rawType.toLowerCase() === "exclude";
    const categories = categoriesFromFilter(effectiveFilter);

    try {
      const forceHideAll = isNone || (!defaultPoiVisible && isAll);
      if (typeof state.map.showsPointsOfInterest !== "undefined") {
        state.map.showsPointsOfInterest = !forceHideAll;
      }

      if (window.mapkit && window.mapkit.PointOfInterestFilter) {
        if (forceHideAll) {
          if (window.mapkit.PointOfInterestFilter.excludingAllCategories) {
            state.map.pointOfInterestFilter = window.mapkit.PointOfInterestFilter.excludingAllCategories;
            return;
          }
        }

        if (isInclude && categories.length > 0) {
          if (typeof window.mapkit.PointOfInterestFilter.including === "function") {
            state.map.pointOfInterestFilter = window.mapkit.PointOfInterestFilter.including(categories);
            return;
          }
        }

        if (isExclude && categories.length > 0) {
          if (typeof window.mapkit.PointOfInterestFilter.excluding === "function") {
            state.map.pointOfInterestFilter = window.mapkit.PointOfInterestFilter.excluding(categories);
            return;
          }
        }

        if (isAll && window.mapkit.PointOfInterestFilter.includingAllCategories) {
          state.map.pointOfInterestFilter = window.mapkit.PointOfInterestFilter.includingAllCategories;
          return;
        }

        state.map.pointOfInterestFilter = null;
      }
    } catch (_) {}
  }

  function loadMapKitScriptIfNeeded() {
    return new Promise((resolve, reject) => {
      if (window.mapkit) {
        resolve();
        return;
      }
      const existing = document.getElementById("apple-mapkit-script");
      if (existing) {
        existing.addEventListener("load", () => resolve());
        existing.addEventListener("error", () => reject(new Error("failed to load mapkit script")));
        return;
      }
      const script = document.createElement("script");
      script.id = "apple-mapkit-script";
      script.src = "https://cdn.apple-mapkit.com/mk/5.x.x/mapkit.js";
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error("failed to load mapkit script"));
      document.head.appendChild(script);
    });
  }

  function waitForCanvasSizeReady(canvasId, timeoutMs) {
    return new Promise((resolve, reject) => {
      const startedAt = Date.now();
      const check = function () {
        const el = document.getElementById(canvasId);
        const width = el ? el.clientWidth : 0;
        const height = el ? el.clientHeight : 0;
        if (width > 0 && height > 0) {
          resolve({ width: width, height: height });
          return;
        }
        if (Date.now() - startedAt > timeoutMs) {
          reject(new Error("mapCanvas size is not ready: " + width + "x" + height));
          return;
        }
        requestAnimationFrame(check);
      };
      check();
    });
  }

  function pointToCoordinate(pageX, pageY) {
    if (!state.map || !window.mapkit) return null;
    try {
      if (typeof state.map.convertPointOnPageToCoordinate === "function") {
        const domPoint =
          (typeof DOMPoint !== "undefined")
            ? new DOMPoint(pageX, pageY)
            : { x: pageX, y: pageY };
        return state.map.convertPointOnPageToCoordinate(domPoint);
      }
      const point = new window.mapkit.Point(pageX, pageY);
      if (typeof state.map.convertPointToCoordinate === "function") {
        return state.map.convertPointToCoordinate(point);
      }
    } catch (_) {}
    return null;
  }

  function coordinateFromMapEvent(event) {
    if (!event) return null;
    if (event.coordinate && event.coordinate.latitude != null && event.coordinate.longitude != null) {
      return event.coordinate;
    }
    if (event.pointOnPage && event.pointOnPage.x != null && event.pointOnPage.y != null) {
      return pointToCoordinate(event.pointOnPage.x, event.pointOnPage.y);
    }
    const pageX = event.pageX != null ? event.pageX :
      (event.domEvent && event.domEvent.pageX != null ? event.domEvent.pageX : null);
    const pageY = event.pageY != null ? event.pageY :
      (event.domEvent && event.domEvent.pageY != null ? event.domEvent.pageY : null);
    if (pageX == null || pageY == null) return null;
    return pointToCoordinate(pageX, pageY);
  }

  function setupLongPressDetection() {
    if (state.longPressHandlersInstalled) return;
    state.longPressHandlersInstalled = true;

    state.map.addEventListener("single-tap", function (event) {
      const c = coordinateFromMapEvent(event);
      if (!c) return;
      debugLog("emit mapTapped lat=" + c.latitude + " lng=" + c.longitude);
      emit({ type: "mapTapped", lat: c.latitude, lng: c.longitude });
    });

    state.map.addEventListener("long-press", function (event) {
      const c = coordinateFromMapEvent(event);
      if (!c) return;
      debugLog("emit longPress lat=" + c.latitude + " lng=" + c.longitude);
      emit({ type: "longPress", lat: c.latitude, lng: c.longitude });
    });
  }

  function attachMapEvents() {
    if (!state.map) return;
    state.map.addEventListener("region-change-end", function () {
      try {
        const r = state.map.region;
        if (!r || !r.center || !r.span) return;
        state.region = {
          centerLat: r.center.latitude,
          centerLng: r.center.longitude,
          latDelta: r.span.latitudeDelta,
          lngDelta: r.span.longitudeDelta,
        };
        renderStatus();
        emit({ type: "regionDidChange", region: state.region });
      } catch (e) {
        emitBridgeError(e && e.message ? e.message : e);
      }
    });

    state.map.addEventListener("region-change-start", function () {
      const shouldEmit = !state.suppressNextRegionWillChange;
      state.suppressNextRegionWillChange = false;
      try {
        const r = state.map.region;
        if (r && r.center && r.span) {
          state.region = {
            centerLat: r.center.latitude,
            centerLng: r.center.longitude,
            latDelta: r.span.latitudeDelta,
            lngDelta: r.span.longitudeDelta,
          };
          renderStatus();
        }
        if (shouldEmit) {
          emit({ type: "regionWillChange", region: state.region });
        }
      } catch (e) {
        emitBridgeError(e && e.message ? e.message : e);
      }
    });


    state.map.addEventListener("select", function (event) {
      try {
        if (event && event.annotation && event.annotation.data && event.annotation.data.id) {
          const id = String(event.annotation.data.id);
          state.lastSelectedAnnotationId = id;
          if (state.pendingDomTapSelectionId === id) {
            state.pendingDomTapSelectionId = null;
          } else {
            emit({ type: "annotationTapped", id: id });
          }
          if (typeof requestAnimationFrame === "function") {
            requestAnimationFrame(function () {
              debugLog("emit annotationSelected id=" + id);
              emit({ type: "annotationSelected", id: id });
            });
          } else {
            emit({ type: "annotationSelected", id: id });
          }
          return;
        }
        if (event && event.overlay && event.overlay.data && event.overlay.data.id) {
          debugLog("emit overlayTapped id=" + String(event.overlay.data.id));
          emit({ type: "overlayTapped", id: String(event.overlay.data.id) });
        }
      } catch (_) {}
    });
    state.map.addEventListener("deselect", function (event) {
      try {
        if (event && event.annotation && event.annotation.data && event.annotation.data.id) {
          const id = String(event.annotation.data.id);
          if (state.lastSelectedAnnotationId === id) {
            state.lastSelectedAnnotationId = null;
          }
          if (typeof requestAnimationFrame === "function") {
            requestAnimationFrame(function () {
              debugLog("emit annotationDeselected id=" + id);
              emit({ type: "annotationDeselected", id: id });
            });
          } else {
            emit({ type: "annotationDeselected", id: id });
          }
        }
      } catch (_) {}
    });

    setupLongPressDetection();
  }

  function applyMapKitRegion(region) {
    if (!state.map || !window.mapkit || !region) return;
    try {
      const current = state.map.region;
      if (current && current.center && current.span) {
        // Tolerate tiny round-trip drift (JS <-> Kotlin <-> MapKit) to avoid
        // re-applying an effectively identical region and retriggering events.
        const latEps = 1e-5;
        const lngEps = 1e-5;
        const spanEps = 1e-5;
        const sameCenter =
          Math.abs(current.center.latitude - region.centerLat) <= latEps &&
          Math.abs(current.center.longitude - region.centerLng) <= lngEps;
        const sameSpan =
          Math.abs(current.span.latitudeDelta - region.latDelta) <= spanEps &&
          Math.abs(current.span.longitudeDelta - region.lngDelta) <= spanEps;
        if (sameCenter && sameSpan) return;
      }
    } catch (_) {}
    const center = new window.mapkit.Coordinate(region.centerLat, region.centerLng);
    const span = new window.mapkit.CoordinateSpan(region.latDelta, region.lngDelta);
    state.map.region = new window.mapkit.CoordinateRegion(center, span);
  }

  function snapshotMapRegion(region) {
    if (!region || !region.center || !region.span) return null;
    return {
      centerLat: region.center.latitude,
      centerLng: region.center.longitude,
      latDelta: region.span.latitudeDelta,
      lngDelta: region.span.longitudeDelta,
    };
  }

  function regionsApproximatelyEqual(a, b) {
    if (!a || !b) return false;
    const coordEpsilon = 1e-6;
    const spanEpsilon = 1e-5;
    return (
      Math.abs(a.centerLat - b.centerLat) <= coordEpsilon &&
      Math.abs(a.centerLng - b.centerLng) <= coordEpsilon &&
      Math.abs(a.latDelta - b.latDelta) <= spanEpsilon &&
      Math.abs(a.lngDelta - b.lngDelta) <= spanEpsilon
    );
  }

  function emitRegionResolution(payload) {
    if (window.AndroidMKBridge && window.AndroidMKBridge.emitRegionResolution) {
      window.AndroidMKBridge.emitRegionResolution(JSON.stringify(payload));
    }
  }

  function waitForAdjustedRegionStability(timeoutMs) {
    return new Promise((resolve, reject) => {
      if (!state.map) {
        reject(new Error("map is not ready"));
        return;
      }

      const startedAt = Date.now();
      const fallbackDelayMs = 48;
      let sawRegionChangeEnd = false;
      let lastRegion = null;
      let stableFrames = 0;
      let finished = false;
      let frameHandle = 0;
      let frameUsesTimeout = false;

      const removeRegionChangeEndListener = function () {
        try {
          if (state.map && typeof state.map.removeEventListener === "function") {
            state.map.removeEventListener("region-change-end", onRegionChangeEnd);
          }
        } catch (_) {}
      };

      const finishSuccess = function (region) {
        if (finished) return;
        finished = true;
        if (frameHandle) {
          if (frameUsesTimeout) clearTimeout(frameHandle);
          else if (typeof cancelAnimationFrame === "function") cancelAnimationFrame(frameHandle);
        }
        removeRegionChangeEndListener();
        resolve(region);
      };

      const finishFailure = function (error) {
        if (finished) return;
        finished = true;
        if (frameHandle) {
          if (frameUsesTimeout) clearTimeout(frameHandle);
          else if (typeof cancelAnimationFrame === "function") cancelAnimationFrame(frameHandle);
        }
        removeRegionChangeEndListener();
        reject(error);
      };

      const onRegionChangeEnd = function () {
        sawRegionChangeEnd = true;
      };

      try {
        state.map.addEventListener("region-change-end", onRegionChangeEnd);
      } catch (_) {}

      const scheduleNext = function () {
        if (typeof requestAnimationFrame === "function") {
          frameUsesTimeout = false;
          frameHandle = requestAnimationFrame(tick);
        } else {
          frameUsesTimeout = true;
          frameHandle = setTimeout(tick, 16);
        }
      };

      const tick = function () {
        if (finished) return;
        if (Date.now() - startedAt > timeoutMs) {
          finishFailure(new Error("timed out waiting for adjusted region stability"));
          return;
        }

        try {
          const current = snapshotMapRegion(state.map.region);
          if (current) {
            if (lastRegion && regionsApproximatelyEqual(lastRegion, current)) {
              stableFrames += 1;
            } else {
              stableFrames = 0;
            }
            lastRegion = current;

            if (stableFrames >= 1 && (sawRegionChangeEnd || Date.now() - startedAt >= fallbackDelayMs)) {
              finishSuccess(current);
              return;
            }
          }
        } catch (e) {
          finishFailure(e);
          return;
        }

        scheduleNext();
      };

      scheduleNext();
    });
  }

  function resolveImageSource(source) {
    if (!source || !source.kind) return null;
    if (source.kind === "MKImageSourceUrl" || source.kind === "url") return source.value || null;
    if (source.kind === "MKImageSourceBase64Png" || source.kind === "base64Png") return "data:image/png;base64," + String(source.value || "");
    if (source.kind === "MKImageSourceResourceName" || source.kind === "resourceName") {
      return "file:///android_res/drawable/" + source.value + ".png";
    }
    return null;
  }

  function clamp01(value, fallback) {
    const n = Number(value);
    if (!Number.isFinite(n)) return fallback;
    if (n < 0) return 0;
    if (n > 1) return 1;
    return n;
  }

  function toPositiveNumber(value, fallback) {
    const n = Number(value);
    if (!Number.isFinite(n) || n <= 0) return fallback;
    return n;
  }

  function createPoint(x, y) {
    if (typeof DOMPoint !== "undefined") return new DOMPoint(x, y);
    return { x: x, y: y };
  }

  function computeAnchorOffset(style) {
    const width = toPositiveNumber(style && style.widthDp, 36);
    const height = toPositiveNumber(style && style.heightDp, 36);
    const anchorX = clamp01(style && style.anchorX, 0.5);
    const anchorY = clamp01(style && style.anchorY, 1.0);
    return createPoint(
      (anchorX - 0.5) * width,
      (anchorY - 1.0) * height
    );
  }

  function buildAnnotation(item) {
    const coord = new window.mapkit.Coordinate(item.lat, item.lng);
    const style = item.style || { kind: "MKAnnotationStyleMarker" };
    let annotation = null;
    try {
      if (style.kind === "MKAnnotationStyleDom" && window.mapkit.Annotation) {
        const sanitizedHtml = sanitizeDomAnnotationHtml(style.html);
        if (sanitizedHtml == null) {
          emitBridgeError("Rejected unsafe HTML for annotation id=" + String(item.id));
          return null;
        }
        annotation = new window.mapkit.Annotation(
          coord,
          function () {
            const wrapper = document.createElement("div");
            wrapper.className = "mapkit-android-dom-annotation";
            wrapper.dataset.annotationId = String(item.id);
            wrapper.innerHTML = sanitizedHtml;
            wrapper.addEventListener("click", function (event) {
              event.stopPropagation();
              emit({ type: "annotationTapped", id: String(item.id) });
              if (style.allowSelection !== false && state.map && "selectedAnnotation" in state.map) {
                state.pendingDomTapSelectionId = String(item.id);
                try {
                  state.map.selectedAnnotation = annotation;
                } catch (_) {
                  state.pendingDomTapSelectionId = null;
                }
              }
            });
            state.annotationElementsById[String(item.id)] = wrapper;
            return wrapper;
          },
          {
            data: { id: item.id },
            calloutEnabled: false,
          }
        );
      } else if ((style.kind === "MKAnnotationStyleImage" || style.kind === "customImage") && window.mapkit.ImageAnnotation) {
        const imageUrl = resolveImageSource(style.source);
        annotation = new window.mapkit.ImageAnnotation(coord, {
          title: item.title || undefined,
          subtitle: item.subtitle || undefined,
          url: { 1: imageUrl, 2: imageUrl, 3: imageUrl },
          anchorOffset: computeAnchorOffset(style),
        });
      } else {
        const options = {
          title: item.title || undefined,
          subtitle: item.subtitle || undefined,
          color: style.tintHex || undefined,
          glyphText: style.glyphText || undefined,
        };
        const glyphImageUrl = resolveImageSource(style.glyphImageSource);
        if (glyphImageUrl) {
          try {
            options.glyphImage = { 1: glyphImageUrl, 2: glyphImageUrl, 3: glyphImageUrl };
            options.glyphText = undefined;
          } catch (_) {}
        }
        annotation = new window.mapkit.MarkerAnnotation(coord, options);
      }
    } catch (_) {
      annotation = new window.mapkit.MarkerAnnotation(coord, {
        title: item.title || undefined,
        subtitle: item.subtitle || undefined,
      });
    }
    if (!annotation) return null;
    annotation.data = { id: item.id };
    try {
      if (typeof item.isDraggable !== "undefined") {
        annotation.draggable = !!item.isDraggable;
      }
    } catch (_) {}
    try {
      annotation.addEventListener("drag-start", function () {
        emit({ type: "annotationDragStart", id: String(item.id) });
      });
      annotation.addEventListener("dragging", function (event) {
        const c = event && event.target && event.target.coordinate;
        if (!c) return;
        emit({
          type: "annotationDragging",
          id: String(item.id),
          lat: c.latitude,
          lng: c.longitude,
        });
      });
      annotation.addEventListener("drag-end", function (event) {
        const c = event && event.target && event.target.coordinate;
        if (!c) return;
        emit({
          type: "annotationDragEnd",
          id: String(item.id),
          lat: c.latitude,
          lng: c.longitude,
        });
      });
    } catch (_) {}
    return annotation;
  }

  function reconcileAnnotations(nextItems) {
    const nextMap = {};
    nextItems.forEach((item) => {
      if (item && item.id) nextMap[item.id] = item;
    });

    Object.keys(state.annotationsById).forEach((id) => {
      if (!nextMap[id]) {
        try {
          state.map.removeAnnotation(state.annotationsById[id]);
        } catch (_) {}
        delete state.annotationsById[id];
        delete state.annotationElementsById[id];
        delete state.annotationHashesById[id];
      }
    });

    Object.keys(nextMap).forEach((id) => {
      const item = nextMap[id];
      if (item && item.isVisible === false) return;
      const style = item && item.style ? item.style : {};
      const isDom = style.kind === "MKAnnotationStyleDom";
      const nextHash = isDom
        ? {
            structure: domAnnotationStructureHash(item),
            content: domAnnotationContentHash(item),
          }
        : stableAnnotationHash(item);
      const prevHash = state.annotationHashesById[id];
      if (isDom && prevHash && state.annotationsById[id]) {
        if (prevHash.structure === nextHash.structure) {
          if (prevHash.content === nextHash.content) return;
          if (updateDomAnnotationContent(id, style.html)) {
            state.annotationHashesById[id] = nextHash;
            return;
          }
        }
      } else if (prevHash === nextHash && state.annotationsById[id]) {
        return;
      }

      if (state.annotationsById[id]) {
        try {
          state.map.removeAnnotation(state.annotationsById[id]);
        } catch (_) {}
      }
      delete state.annotationElementsById[id];
      const marker = buildAnnotation(item);
      if (!marker) return;
      state.map.addAnnotation(marker);
      state.annotationsById[id] = marker;
      state.annotationHashesById[id] = nextHash;
    });
  }

  function applyOverlayStyle(item) {
    const dash = Array.isArray(item.lineDashPattern) && item.lineDashPattern.length > 0
      ? item.lineDashPattern
      : undefined;
    return new window.mapkit.Style({
      lineWidth: item.strokeWidth || 3,
      strokeColor: item.strokeColor || "#0EA5E9",
      fillColor: item.fillColor || undefined,
      lineDash: dash,
    });
  }

  function buildPolyline(item) {
    const points = (item.points || []).map((p) => new window.mapkit.Coordinate(p.lat, p.lng));
    if (!points.length) return null;
    return new window.mapkit.PolylineOverlay(points, {
      style: applyOverlayStyle(item),
    });
  }

  function buildPolygon(item) {
    const points = (item.points || []).map((p) => new window.mapkit.Coordinate(p.lat, p.lng));
    if (points.length < 3) return null;
    return new window.mapkit.PolygonOverlay(points, {
      style: applyOverlayStyle(item),
    });
  }

  function buildCircle(item) {
    if (typeof item.centerLat === "undefined" || typeof item.centerLng === "undefined") return null;
    const center = new window.mapkit.Coordinate(item.centerLat, item.centerLng);
    return new window.mapkit.CircleOverlay(center, item.radiusMeter || 100, {
      style: applyOverlayStyle(item),
    });
  }

  function reconcileOverlays(nextItems) {
    const nextMap = {};
    nextItems.forEach((item) => {
      if (item && item.id) nextMap[item.id] = item;
    });

    Object.keys(state.overlaysById).forEach((id) => {
      if (!nextMap[id]) {
        try {
          state.map.removeOverlay(state.overlaysById[id]);
        } catch (_) {}
        delete state.overlaysById[id];
        delete state.overlayHashesById[id];
      }
    });

    Object.keys(nextMap).forEach((id) => {
      const item = nextMap[id];
      const nextHash = stableHash(item);
      const prevHash = state.overlayHashesById[id];
      if (prevHash === nextHash && state.overlaysById[id]) return;

      if (state.overlaysById[id]) {
        try {
          state.map.removeOverlay(state.overlaysById[id]);
        } catch (_) {}
      }

      let overlay = null;
      if (item.type === "MKPolylineOverlay") {
        overlay = buildPolyline(item);
      } else if (item.type === "MKPolygonOverlay") {
        overlay = buildPolygon(item);
      } else if (item.type === "MKCircleOverlay") {
        overlay = buildCircle(item);
      }
      if (!overlay) return;

      overlay.data = { id: item.id };
      state.map.addOverlay(overlay);
      state.overlaysById[id] = overlay;
      state.overlayHashesById[id] = nextHash;
    });
  }

  function applyMapOptions() {
    if (!state.mapReady || !state.map) return;
    const effectivePoi = !!state.showsPointsOfInterest;

    try {
      const nextMapType = mapTypeFor(state.mapStyle);
      if (nextMapType) {
        state.map.mapType = nextMapType;
      }
    } catch (_) {}

    try {
      const nextScheme = colorSchemeFor(state.appearance);
      if (nextScheme) {
        state.map.colorScheme = nextScheme;
      }
    } catch (_) {}

    try {
      state.map.showsPointsOfInterest = !!effectivePoi;
    } catch (_) {}
    applyPoiFilter(state.poiFilter, effectivePoi);

    try {
      state.map.showsCompass = featureVisibilityFor(!!state.showsCompass);
    } catch (_) {}

    try {
      state.map.showsScale = featureVisibilityFor(!!state.showsScale);
    } catch (_) {}

    try {
      state.map.isRotationEnabled = !!state.isRotateEnabled;
      state.map.isScrollEnabled = !!state.isScrollEnabled;
      state.map.isZoomEnabled = !!state.isZoomEnabled;
      state.map.isPitchEnabled = !!state.isPitchEnabled;
    } catch (_) {}

    try {
      if (typeof state.map.showsMapTypeControl !== "undefined") {
        state.map.showsMapTypeControl = !!state.showsMapTypeControl;
      }
    } catch (_) {}
    try {
      if (typeof state.map.showsZoomControl !== "undefined") {
        state.map.showsZoomControl = !!state.showsZoomControl;
      }
    } catch (_) {}

    debugLog(
      "applyMapOptions style=" + state.mapStyle +
      " poi=" + effectivePoi +
      " compass=" + state.showsCompass +
      " zoomControl=" + state.showsZoomControl +
      " mapType=" + String(state.map.mapType)
    );
  }

  function initializeMapKit() {
    return loadMapKitScriptIfNeeded().then(() => {
      if (!window.mapkit) throw new Error("mapkit is unavailable");
      if (!state.token || !String(state.token).startsWith("eyJ")) {
        emitBridgeError("MAPKIT_JS_TOKEN does not look like Apple JWT token (expected prefix: eyJ...)");
      }

      const initOptions = {
        authorizationCallback: function (done) {
          done(state.token);
        },
      };
      if (state.language && state.language !== "auto") {
        initOptions.language = state.language;
      }
      window.mapkit.init(initOptions);

      const mapCanvasId = "mapCanvas";
      return waitForCanvasSizeReady(mapCanvasId, 4000).then(function (size) {
        debugLog("mapCanvas size: " + size.width + "x" + size.height);
        state.map = new window.mapkit.Map(mapCanvasId, {
          isRotationEnabled: true,
          isZoomEnabled: true,
          isScrollEnabled: true,
        });

        state.map.addEventListener("error", function (e) {
          const msg = e && e.message ? e.message : "map error";
          emitBridgeError("MapKit map error: " + msg);
        });

        // Apply initial region before attaching listeners so bootstrap transitions
        // (for example world -> target region) are not emitted to app callbacks.
        applyMapKitRegion(state.region);
        state.mapReady = true;
        attachMapEvents();
        applyMapOptions();
        debugLog("map instance created");
      });
    });
  }

  function applyMapUserLocationConfig() {
    if (!state.mapReady || !state.map) return;
    try {
      if (typeof state.map.showsUserLocation !== "undefined") {
        state.map.showsUserLocation = !!state.userLocationEnabled;
      }
    } catch (_) {}
    if (typeof navigator === "undefined" || !navigator.geolocation) return;

    if (state.userLocationEnabled && state.userLocationWatchId == null) {
      state.userLocationWatchId = navigator.geolocation.watchPosition(
        function (position) {
          if (!position || !position.coords) return;
          emit({
            type: "userLocationUpdated",
            lat: position.coords.latitude,
            lng: position.coords.longitude,
          });
        },
        function (error) {
          const reason = error && error.message ? error.message : "unknown geolocation error";
          emitBridgeError("geolocation watchPosition failed: " + reason);
        },
        {
          enableHighAccuracy: true,
          maximumAge: 1000,
          timeout: 10000,
        }
      );
    }

    if (!state.userLocationEnabled && state.userLocationWatchId != null) {
      try {
        navigator.geolocation.clearWatch(state.userLocationWatchId);
      } catch (_) {}
      state.userLocationWatchId = null;
    }
  }

  function applyContentStateToMap() {
    if (!state.mapReady || !state.map) return;
    applyMapOptions();
    reconcileAnnotations(state.annotations || []);
    reconcileOverlays(state.overlays || []);
    applyMapUserLocationConfig();
    renderStatus();
  }

  function applyRegionStateToMap() {
    if (!state.mapReady || !state.map) return;
    applyMapKitRegion(state.region);
    renderStatus();
  }

  function applyStateToMap() {
    applyRegionStateToMap();
    applyContentStateToMap();
  }

  window.MKBridge = {
    init: function (token) {
      state.token = token;
      initializeMapKit()
        .then(function () {
          applyStateToMap();
          emit({ type: "mapLoaded" });
        })
        .catch(function (e) {
          state.mapReady = false;
          renderStatus();
          emitBridgeError(e && e.message ? e.message : e);
        });
    },

    applyState: function (payload) {
      debugLog("applyState called");
      if (payload && payload.annotations) state.annotations = payload.annotations;
      if (payload && payload.overlays) state.overlays = payload.overlays;

      if (payload && payload.mapStyle) state.mapStyle = payload.mapStyle;
      if (payload && payload.appearance) state.appearance = payload.appearance;
      if (payload && payload.language) state.language = payload.language;
      if (payload && typeof payload.showsCompass !== "undefined") state.showsCompass = !!payload.showsCompass;
      if (payload && typeof payload.showsScale !== "undefined") state.showsScale = !!payload.showsScale;
      if (payload && typeof payload.showsZoomControl !== "undefined") {
        state.showsZoomControl = !!payload.showsZoomControl;
      }
      if (payload && typeof payload.showsMapTypeControl !== "undefined") {
        state.showsMapTypeControl = !!payload.showsMapTypeControl;
      }
      if (payload && typeof payload.showsPointsOfInterest !== "undefined") {
        state.showsPointsOfInterest = !!payload.showsPointsOfInterest;
      }
      if (payload && payload.poiFilter) {
        state.poiFilter = payload.poiFilter;
      }
      if (payload && typeof payload.isRotateEnabled !== "undefined") state.isRotateEnabled = !!payload.isRotateEnabled;
      if (payload && typeof payload.isScrollEnabled !== "undefined") state.isScrollEnabled = !!payload.isScrollEnabled;
      if (payload && typeof payload.isZoomEnabled !== "undefined") state.isZoomEnabled = !!payload.isZoomEnabled;
      if (payload && typeof payload.isPitchEnabled !== "undefined") state.isPitchEnabled = !!payload.isPitchEnabled;

      if (payload && typeof payload.userLocationEnabled !== "undefined") {
        state.userLocationEnabled = !!payload.userLocationEnabled;
      }
      if (payload && typeof payload.userLocationFollowsHeading !== "undefined") {
        state.userLocationFollowsHeading = !!payload.userLocationFollowsHeading;
      }
      if (payload && typeof payload.userLocationShowsAccuracyRing !== "undefined") {
        state.userLocationShowsAccuracyRing = !!payload.userLocationShowsAccuracyRing;
      }

      try {
        applyContentStateToMap();
      } catch (e) {
        emitBridgeError(e && e.message ? e.message : e);
      }
    },

    applyRegion: function (region) {
      if (region) state.region = region;
      try {
        applyRegionStateToMap();
      } catch (e) {
        emitBridgeError(e && e.message ? e.message : e);
      }
    },

    resolveAdjustedRegion: function (region) {
      if (!region) {
        emitRegionResolution({
          ok: false,
          errorType: "bridgeFailure",
          message: "region is required",
        });
        return;
      }
      if (!state.mapReady || !state.map) {
        emitRegionResolution({
          ok: false,
          errorType: "bridgeFailure",
          message: "map is not ready",
        });
        return;
      }
      try {
        applyMapKitRegion(region);
      } catch (e) {
        emitRegionResolution({
          ok: false,
          errorType: "bridgeFailure",
          message: e && e.message ? e.message : String(e),
        });
        return;
      }

      waitForAdjustedRegionStability(4000)
        .then(function (adjustedRegion) {
          state.region = adjustedRegion;
          renderStatus();
          emitRegionResolution({
            ok: true,
            region: adjustedRegion,
          });
        })
        .catch(function (e) {
          const message = e && e.message ? e.message : String(e);
          if (message.indexOf("timed out") >= 0) {
            emitRegionResolution({
              ok: false,
              errorType: "timeout",
              phase: "adjustedRegion",
              timeoutMs: 4000,
              message: message,
            });
            return;
          }
          emitRegionResolution({
            ok: false,
            errorType: "bridgeFailure",
            message: message,
          });
        });
    },

    selectAnnotationById: function (id, animated) {
      if (!state.map || !id) return;
      const annotation = state.annotationsById[String(id)];
      if (!annotation) return;
      try {
        if ("selectedAnnotation" in state.map) {
          state.map.selectedAnnotation = annotation;
          debugLog("selectAnnotationById: " + id);
        }
      } catch (_) {}
    },

    deselectAnnotation: function (animated) {
      if (!state.map) return;
      try {
        if ("selectedAnnotation" in state.map) {
          state.map.selectedAnnotation = null;
          debugLog("deselectAnnotation");
        }
      } catch (_) {}
    },

    simulatePan: function () {
      if (!state.mapReady) return;
      state.region.centerLat = state.region.centerLat + 0.001;
      state.region.centerLng = state.region.centerLng + 0.001;
      applyMapKitRegion(state.region);
      emit({ type: "regionDidChange", region: state.region });
      renderStatus();
    },

    simulateAnnotationTap: function () {
      const id = (state.annotations[0] && state.annotations[0].id) || "sample-annotation";
      emit({ type: "annotationSelected", id: id });
    },

    simulateOverlayTap: function () {
      const id = (state.overlays[0] && state.overlays[0].id) || "sample-overlay";
      emit({ type: "overlayTapped", id: id });
    },
  };

  renderStatus();
})();
