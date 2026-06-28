# MapKit JS HTML Annotation 追加メモ

MapKit JS には `mapkit.Annotation` があり、factory function から `HTMLElement` を返すことで、地図上に任意の DOM をアノテーションとして表示できる。

今回やりたいのは、Android 側から渡された HTML 文字列を JS 側にブリッジし、それを `mapkit.Annotation` の DOM として表示すること。

Android 側の公開 API はまだ再考するため、この文書では確定しない。  
重要なのは、JS 側で次のような処理をできるようにすること。

```js
function createHtmlAnnotation(spec) {
  const annotation = new mapkit.Annotation(
    new mapkit.Coordinate(spec.latitude, spec.longitude),
    function() {
      const wrapper = document.createElement("div");
      wrapper.className = "mapkit-android-html-annotation";
      wrapper.dataset.annotationId = spec.id;
      wrapper.innerHTML = spec.html;
      return wrapper;
    },
    {
      data: {
        id: spec.id
      },
      calloutEnabled: false
    }
  );

  map.addAnnotation(annotation);
  return annotation;
}
```

`spec.html` には、例えば次のような単一HTML文字列を渡す想定。

```html
<div style="background:white;border-radius:8px;padding:4px 8px;box-shadow:0 2px 8px rgba(0,0,0,.25);white-space:nowrap;">
  ¥980
</div>
```

CSSは初期実装では分離しない。  
inline style を含んだ HTML 文字列をそのまま渡せばよい。

JS 側では、必要に応じて `id -> annotation` と `id -> element` を保持する。

```js
const htmlAnnotationsById = new Map();
const htmlAnnotationElementsById = new Map();
```

factory 内で生成した DOM を保持しておくと、HTML更新時に annotation を作り直さず `innerHTML` だけ差し替えられる。

```js
function updateHtmlAnnotation(id, html) {
  const element = htmlAnnotationElementsById.get(id);
  if (!element) return false;

  element.innerHTML = html;
  return true;
}
```

クリックは DOM 側で拾って、既存の Android bridge に id を返す。

```js
wrapper.addEventListener("click", function(event) {
  event.stopPropagation();
  AndroidMapKitBridge.onHtmlAnnotationClick(spec.id);
});
```

実装上の注意点は以下。

- HTML は信頼済み入力を前提にする
- `<script>` は許可しない方がよい
- `onclick` などの inline event handler も避ける
- 初期実装では CSS 分離や DOM DSL は不要
- MapKit JS 側では `mapkit.Annotation` + `HTMLElement` factory を使う

要するに、Android 側から `id / coordinate / html` を JS に渡し、JS 側で `mapkit.Annotation` の factory から `HTMLElement` を返せるようにする。