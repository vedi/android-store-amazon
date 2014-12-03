### v2.0.0 [view commit logs](https://github.com/soomla/android-store/compare/v1.0.0...v2.0.0)

*** Migrating from IAP v1.0 to IAP v2.0 ***:
  1. Add `in-app-purchasing-2.0.1.jar` instead of `in-app-purchasing-1.0.3.jar`
  1. Change `ResponseReceiver` package name in `AndroidManifest.xml` to `com.amazon.device.iap`:
  ```xml
  <receiver android:name = "com.amazon.device.iap.ResponseReceiver" >
    ...
    </receiver>
    ```

  1. Read more about [Migrating from IAP v1.0 to IAP v2.0](https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs-v2/migrate-iapv1-apps-to-iapv2)
