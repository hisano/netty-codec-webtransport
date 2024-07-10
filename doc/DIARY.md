## 開発日誌

### 2024/07/01(月)

- Netty向けWebTransportコーデックの開発開始
- WebTransportの各種仕様を調査
- プロジェクトの作成

### 2024/07/02(火)

- ライセンスファイルの追加
- mvn-wrapperによるビルド環境の整備
- テスト環境の整備
- Playwright環境を整備

### 2024/07/03(水)

- Netty HTTP/3環境を整備
- Netty HTTP/3のテストケースを追加

### 2024/07/06(土)

- Playwright + Chromeを使ったHTTP/3のテストケースを追加
- WebTransportのコネクション確立処理を実装

#### 調査メモ

- 自己署名証明書の制限
  - [ECDSAを使う必要あり](https://stackoverflow.com/questions/75979276/do-i-have-to-get-a-valid-ssl-certificate-to-make-webtranport-server-examples-wor)
  - [期限は二週間以内](https://qiita.com/alivelime/items/e5c75288f56cd0949dca)
- [ChromiumのWebTransport実装](https://chromium.googlesource.com/chromium/src/+/d622da780b2abe8ae376506323c3a3d26e9ac7da/third_party/blink/renderer/modules/webtransport)

### 2024/07/07(日)

- クライアント→サーバのBidirectional Streamに対応

### 2024/07/08(月)

- WebTransportStreamFrameクラスを追加

### 2024/07/09(火)

- クラス構成を整理

### 2024/07/10(水)

- サーバ→クライアントのBidirectional Streamに対応
