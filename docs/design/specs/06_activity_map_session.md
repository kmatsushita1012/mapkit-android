# Activity-owned MapKit session

## External interface

`MKMapSession(activityContext)` owns a single MapKit WebView. A screen passes it as
`MKMapView(session = session, ...)` and the Activity calls `session.close()` in
`onDestroy`. Omitting `session` retains the usual independent view behavior.

## Internal specification

The Compose view takes the existing WebView from its previous parent and binds the
current controller and event callbacks. Its region, annotations, overlays, and
options are applied through the existing bridge. The page and JavaScript map are
not reloaded while the session stays open, provided the `MKMapKitConfig` does not
change. A previous composable can release the view only while it still owns it.
Closing the session removes and destroys the WebView.
