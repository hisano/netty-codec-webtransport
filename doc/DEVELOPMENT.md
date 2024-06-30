## 差別化の方向性

- NettyのHTTP/3コーデックとの共存をサポート
- 実ブラウザを使ったE2Eテストで品質担保
  - PlaywrightでChrome/Edge/Firefoxでテスト
- `WebTransport over HTTP/2`や`WebTransport over WebSocket`のサポート
- [qlog/qviz](https://github.com/quiclog/qvis) での分析をサポート？
- WebTransport上に作られた他のプロトコルのサポート？

## 調査

- [WebTransport over HTTP/3のプロトコル概要](https://asnokaze.hatenablog.com/entry/2021/04/18/235837)
- [The WebTransport Protocol Framework](https://www.ietf.org/archive/id/draft-ietf-webtrans-overview-07.html)
- [WebTransport over HTTP/3](https://www.ietf.org/archive/id/draft-ietf-webtrans-http3-09.html)

- [ブラウザのWebTransport API概要](https://tech.aptpod.co.jp/entry/2022/05/17/100000)
    - [Google社のサンプル](https://github.com/GoogleChrome/samples/tree/gh-pages/webtransport)
- [WebTransportに至るまでの歴史](https://qiita.com/yuki_uchida/items/d9de148bb2ee418563cf)
    - Chromeの起動時に証明書エラーを無視するコマンドラインの記述例あり
- [MDNのWebTransport API](https://developer.mozilla.org/en-US/docs/Web/API/WebTransport_API)

## WebSocketに対するメリット

- UDPライクなデータグラムをサポート
- WebWorkerで利用可能
