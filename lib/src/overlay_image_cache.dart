part of naver_map_plugin;

class NaverOverlayImageCache {
  NaverOverlayImageCache._();
  static final instance = NaverOverlayImageCache._();

  final Map<String, Future<OverlayImage>> _inflight =
      <String, Future<OverlayImage>>{};
  final _LruMap<String, OverlayImage> _lru = _LruMap<String, OverlayImage>(
    capacity: 128,
  );

  Future<OverlayImage> getFromAsset(
    String assetName, {
    double? devicePixelRatio,
    Size? size,
    AssetBundle? bundle,
    Locale? locale,
    TextDirection? textDirection,
  }) {
    final String key =
        'asset:$assetName|dpr:${devicePixelRatio ?? 0}|w:${size?.width ?? 0}|h:${size?.height ?? 0}|loc:${locale?.toLanguageTag() ?? ''}|td:${textDirection ?? ''}';

    final OverlayImage? cached = _lru[key];
    if (cached != null) return Future<OverlayImage>.value(cached);

    final Future<OverlayImage>? inFlight = _inflight[key];
    if (inFlight != null) return inFlight;

    final Future<OverlayImage> fut =
        OverlayImage.fromAssetImage(
              assetName: assetName,
              devicePixelRatio: devicePixelRatio,
              size: size,
              bundle: bundle,
              locale: locale,
              textDirection: textDirection,
            )
            .then((OverlayImage oi) {
              _lru[key] = oi;
              _inflight.remove(key);
              return oi;
            })
            .catchError((Object e, StackTrace st) {
              _inflight.remove(key);
              throw e;
            });

    _inflight[key] = fut;
    return fut;
  }

  Future<OverlayImage> getFromBitmapAsset(
    String assetName, {
    String? cacheKey,
    double? devicePixelRatio,
    int? targetWidth,
    int? targetHeight,
    AssetBundle? bundle,
  }) async {
    final String key =
        cacheKey ??
        'bmp:$assetName|dpr:${devicePixelRatio ?? 0}|w:${targetWidth ?? 0}|h:${targetHeight ?? 0}';

    final OverlayImage? cached = _lru[key];
    if (cached != null) return cached;

    final Future<OverlayImage>? inFlight = _inflight[key];
    if (inFlight != null) return inFlight;

    final AssetBundle effectiveBundle = bundle ?? rootBundle;

    final Future<OverlayImage> fut = (() async {
      final ByteData data = await effectiveBundle.load(assetName);
      Uint8List bytes = data.buffer.asUint8List();

      if ((targetWidth ?? 0) > 0 || (targetHeight ?? 0) > 0) {
        final ui.Codec codec = await ui.instantiateImageCodec(
          bytes,
          targetWidth: targetWidth,
          targetHeight: targetHeight,
        );
        final ui.FrameInfo frame = await codec.getNextFrame();
        final ByteData? out = await frame.image.toByteData(
          format: ui.ImageByteFormat.png,
        );
        if (out != null) {
          bytes = out.buffer.asUint8List();
        }
      }

      final OverlayImage oi = OverlayImage.fromBitmap(key, bytes);
      _lru[key] = oi;
      _inflight.remove(key);
      return oi;
    })();

    _inflight[key] = fut;
    return fut;
  }

  Future<void> prewarmAssets(
    Iterable<String> assetNames, {
    double? devicePixelRatio,
    Size? size,
  }) async {
    final Set<String> seen = <String>{};
    final List<Future<OverlayImage>> tasks = <Future<OverlayImage>>[];
    for (final String name in assetNames) {
      if (seen.add(name)) {
        tasks.add(
          getFromAsset(name, devicePixelRatio: devicePixelRatio, size: size),
        );
        if (seen.length >= 64) break;
      }
    }
    await Future.wait(tasks);
  }
}

class _LruMap<K, V> {
  _LruMap({required this.capacity});
  final int capacity;
  final LinkedHashMap<K, V> _map = LinkedHashMap<K, V>();

  V? operator [](K key) {
    final V? v = _map.remove(key);
    if (v != null) _map[key] = v; // move to MRU tail
    return v;
  }

  void operator []=(K key, V value) {
    if (_map.length >= capacity && !_map.containsKey(key)) {
      _map.remove(_map.keys.first);
    }
    _map.remove(key);
    _map[key] = value;
  }
}
