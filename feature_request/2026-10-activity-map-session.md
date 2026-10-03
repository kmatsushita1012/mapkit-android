# Activity-owned MapKit session

## External interface

- An Activity may create one `MKMapSession(activity)` after `MKMapKit.init`.
- Pass that session to successive `MKMapView(session = session, ...)` calls.
- Close the session from the Activity's `onDestroy`.
- `MKMapView` without a session keeps its existing independent WebView behavior.

## Internal behavior

- The session owns one `MKBridgeWebView` and detaches it from its previous Compose host before attaching it to the next one.
- The current map content, region, options, controller, and event callbacks replace the previous screen's values.
- The HTML page and `mapkit.Map` instance remain alive between composables. Closing the session destroys the WebView.
- Only one `MKMapView` may display a session at a time.
