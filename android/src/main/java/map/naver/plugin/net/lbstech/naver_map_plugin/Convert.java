package map.naver.plugin.net.lbstech.naver_map_plugin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.overlay.OverlayImage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.loader.FlutterLoader;
import android.util.LruCache;

@SuppressWarnings("rawtypes")
class Convert {

    private static Context applicationContext;
    private static final Object assetCacheLock = new Object();
    private static final LruCache<String, OverlayImage> assetOverlayCache = new LruCache<>(64);
    private static final ConcurrentHashMap<String, FutureTask<OverlayImage>> inFlightDecodes = new ConcurrentHashMap<>();
    private static final ExecutorService assetDecodeExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
    );

    static void init(Context context) {
        applicationContext = context == null ? null : context.getApplicationContext();
    }

    @SuppressWarnings("ConstantConditions")
    static void carveMapOptions(NaverMapOptionSink sink, Map<String, Object> options) {
        if (options.containsKey("mapType"))
            sink.setMapType((Integer) options.get("mapType"));
        if (options.containsKey("liteModeEnable"))
            sink.setLiteModeEnable((Boolean) options.get("liteModeEnable"));
        if (options.containsKey("nightModeEnable"))
            sink.setNightModeEnable((Boolean) options.get("nightModeEnable"));
        if (options.containsKey("indoorEnable"))
            sink.setIndoorEnable((Boolean) options.get("indoorEnable"));
        if (options.containsKey("buildingHeight"))
            sink.setBuildingHeight((Double) options.get("buildingHeight"));
        if (options.containsKey("symbolScale"))
            sink.setSymbolScale((Double) options.get("symbolScale"));
        if (options.containsKey("symbolPerspectiveRatio"))
            sink.setSymbolPerspectiveRatio((Double) options.get("symbolPerspectiveRatio"));
        if (options.containsKey("activeLayers"))
            sink.setActiveLayers((List) options.get("activeLayers"));
        if (options.containsKey("locationButtonEnable"))
            sink.setLocationButtonEnable((Boolean) options.get("locationButtonEnable"));
        if (options.containsKey("scrollGestureEnable"))
            sink.setScrollGestureEnable((Boolean) options.get("scrollGestureEnable"));
        if (options.containsKey("zoomGestureEnable"))
            sink.setZoomGestureEnable((Boolean) options.get("zoomGestureEnable"));
        if (options.containsKey("rotationGestureEnable"))
            sink.setRotationGestureEnable((Boolean) options.get("rotationGestureEnable"));
        if (options.containsKey("tiltGestureEnable"))
            sink.setTiltGestureEnable((Boolean) options.get("tiltGestureEnable"));
        if (options.containsKey("locationButtonEnable"))
            sink.setLocationButtonEnable((Boolean) options.get("locationButtonEnable"));
        if (options.containsKey("locationTrackingMode"))
            sink.setLocationTrackingMode((Integer) options.get("locationTrackingMode"));
        if (options.containsKey("contentPadding"))
            sink.setContentPadding(toDoubleList(options.get("contentPadding")));
        if (options.containsKey("maxZoom"))
            sink.setMaxZoom((Double) options.get("maxZoom"));
        if (options.containsKey("minZoom"))
            sink.setMinZoom((Double) options.get("minZoom"));
        if (options.containsKey("locale"))
            sink.setLocale((String) options.get("locale"));
    }

    @SuppressWarnings("unchecked")
    static LatLng toLatLng(Object o) {
        final List<Double> data = (List<Double>) o;
        return new LatLng(data.get(0), data.get(1));
    }

    static PointF toPoint(Object o) {
        final List data = (List) o;
        return new PointF(toFloat(data.get(0)), toFloat(data.get(1)));
    }

    static List<Double> toDoubleList(Object o) {
        final List data = (List) o;
        ArrayList<Double> result = new ArrayList<>();
        for (Object element : data) {
            if (element instanceof Double) {
                result.add((Double) element);
            }
        }
        return result;
    }

    private static LatLngBounds toLatLngBounds(Object o) {
        if (o == null) {
            return null;
        }
        final List data = (List) o;
        return new LatLngBounds(toLatLng(data.get(0)), toLatLng(data.get(1)));
    }

    @SuppressLint("Assert")
    static CameraPosition toCameraPosition(Map<String, Object> cameraPositionMap) {
        double bearing, tilt, zoom;
        bearing = (double) cameraPositionMap.get("bearing");
        tilt = (double) cameraPositionMap.get("tilt");
        zoom = (double) cameraPositionMap.get("zoom");

        List target = (List) cameraPositionMap.get("target");
        assert target != null && target.size() == 2;
        double lat = (double) target.get(0);
        double lng = (double) target.get(1);
        return new CameraPosition(new LatLng(lat, lng), zoom, tilt, bearing);
    }

    @SuppressWarnings("unchecked")
    static CameraUpdate toCameraUpdate(Object o, float density) {

        Map<String, Object> map = (HashMap<String, Object>) o;

        if (map.isEmpty())
            throw new IllegalArgumentException("Cannot interpret " + o + " as CameraUpdate");

        Map<String, Object> position = (Map<String, Object>) map.get("newCameraPosition");
        if (position != null)
            return CameraUpdate.toCameraPosition(toCameraPosition(position));

        Object scrollTo = map.get("scrollTo");
        if (scrollTo != null) {
            LatLng latLng = toLatLng(scrollTo);
            if (map.containsKey("zoomTo")) {
                double zoomTo = (double) map.get("zoomTo");
                if (zoomTo == 0.0)
                    return CameraUpdate.scrollTo(latLng);
                else
                    return CameraUpdate.scrollAndZoomTo(latLng, zoomTo);
            }
        }

        if (map.containsKey("zoomIn"))
            return CameraUpdate.zoomIn();

        if (map.containsKey("zoomOut"))
            return CameraUpdate.zoomOut();

        if (map.containsKey("zoomBy")) {
            double zoomBy = (double) map.get("zoomBy");
            if (zoomBy != 0.0)
                return CameraUpdate.zoomBy(zoomBy);
        }

        List fitBounds = (List) map.get("fitBounds");
        if (fitBounds != null) {
            int dp = (int) fitBounds.get(1);
            int px = Math.round(dp * density);
            return CameraUpdate.fitBounds(toLatLngBounds(fitBounds.get(0)), px);
        }

        return null;
    }

    static Object cameraPositionToJson(CameraPosition position) {
        if (position == null) {
            return null;
        }
        final Map<String, Object> data = new HashMap<>();
        data.put("bearing", position.bearing);
        data.put("target", latLngToJson(position.target));
        data.put("tilt", position.tilt);
        data.put("zoom", position.zoom);
        return data;
    }

    static Object latlngBoundsToJson(LatLngBounds latLngBounds) {
        final Map<String, Object> arguments = new HashMap<>(2);
        arguments.put("southwest", latLngToJson(latLngBounds.getSouthWest()));
        arguments.put("northeast", latLngToJson(latLngBounds.getNorthEast()));
        return arguments;
    }

    static Object latLngToJson(LatLng latLng) {
        return Arrays.asList(latLng.latitude, latLng.longitude);
    }

    static float toFloat(Object o) {
        return ((Number) o).floatValue();
    }

    static OverlayImage toOverlayImage(Object o) {
        String assetName = (String) o;
        if (applicationContext == null || assetName == null) return null;

        // Normalize potential "asset:" prefix from Flutter
        String normalized = assetName.startsWith("asset:") ? assetName.substring(6) : assetName;
        if (normalized.startsWith("flutter_assets/")) {
            normalized = normalized.substring("flutter_assets/".length());
        }

        FlutterLoader loader = FlutterInjector.instance().flutterLoader();
        String lookupKey = loader.getLookupKeyForAsset(normalized);
        String assetPath = "flutter_assets/" + lookupKey;

        // LRU cache hit
        synchronized (assetCacheLock) {
            OverlayImage cached = assetOverlayCache.get(assetPath);
            if (cached != null) return cached;
        }

        // Fast non-blocking path: return fromAsset wrapper (SDK will decode lazily)
        OverlayImage oi = OverlayImage.fromAsset(assetPath);
        synchronized (assetCacheLock) {
            assetOverlayCache.put(assetPath, oi);
        }
        return oi;
    }

    // Background preloading with future de-duplication. Decodes the PNG and stores OverlayImage in LRU.
    static void preloadOverlayImage(Object o) {
        String assetPath = resolveAssetPath(o);
        if (assetPath == null) return;
        synchronized (assetCacheLock) {
            if (assetOverlayCache.get(assetPath) != null) return;
        }
        inFlightDecodes.computeIfAbsent(assetPath, key -> {
            FutureTask<OverlayImage> task = new FutureTask<>(new Callable<OverlayImage>() {
                @Override
                public OverlayImage call() {
                    // Convert back to lookupKey for AssetManager.open
                    String lookupKey = key.startsWith("flutter_assets/") ? key.substring("flutter_assets/".length()) : key;
                    AssetManager am = applicationContext.getAssets();
                    InputStream is = null;
                    try {
                        is = am.open(lookupKey);
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        if (bmp == null) return null;
                        OverlayImage decoded = OverlayImage.fromBitmap(bmp);
                        synchronized (assetCacheLock) { assetOverlayCache.put(key, decoded); }
                        return decoded;
                    } catch (IOException ignored) {
                        return null;
                    } finally {
                        if (is != null) { try { is.close(); } catch (IOException ignored2) {} }
                        inFlightDecodes.remove(key);
                    }
                }
            });
            assetDecodeExecutor.submit(task);
            return task;
        });
    }

    static void preloadOverlayImages(List<?> icons) {
        if (icons == null || icons.isEmpty()) return;
        int count = 0;
        for (Object icon : icons) {
            preloadOverlayImage(icon);
            if (++count >= 64) break;
        }
    }

    private static String resolveAssetPath(Object o) {
        if (!(o instanceof String)) return null;
        String assetName = (String) o;
        if (applicationContext == null || assetName == null) return null;
        String normalized = assetName.startsWith("asset:") ? assetName.substring(6) : assetName;
        if (normalized.startsWith("flutter_assets/")) {
            normalized = normalized.substring("flutter_assets/".length());
        }
        FlutterLoader loader = FlutterInjector.instance().flutterLoader();
        String lookupKey = loader.getLookupKeyForAsset(normalized);
        return "flutter_assets/" + lookupKey;
    }

    private static final Map<Object, OverlayImage> cachedOverlayImage = new HashMap();

    static OverlayImage toOverlayImageFromBitmap(Object bitMapCacheKey, Object o) {
        if (cachedOverlayImage.containsKey(bitMapCacheKey)) {
            return cachedOverlayImage.get(bitMapCacheKey);
        } else {
            byte[] bytes = (byte[]) o;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            OverlayImage oi = OverlayImage.fromBitmap(bitmap);
            cachedOverlayImage.put(bitMapCacheKey, oi);
            return oi;
        }
    }

    static List<LatLng> toCoords(Object o) {
        final List<?> data = (List) o;
        final List<LatLng> points = new ArrayList<>(data.size());

        for (Object ob : data) {
            final List<?> point = (List) ob;
            points.add(new LatLng(toFloat(point.get(0)), toFloat(point.get(1))));
        }
        return points;
    }

    static List<List<LatLng>> toHoles(Object o) {
        final List<?> data = (List) o;
        final List<List<LatLng>> holes = new ArrayList<>(data.size());

        for (Object ob : data) {
            List<LatLng> hole = toCoords(ob);
            holes.add(hole);
        }
        return holes;
    }

    @SuppressWarnings("MalformedFormatString")
    static int toColorInt(Object value) {
        if (value instanceof Long || value instanceof Integer) {
            String formed = String.format("#%08x", value);
            return Color.parseColor(formed);
        } else {
            return 0;
        }
    }

}