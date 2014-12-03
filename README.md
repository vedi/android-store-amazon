*This project is a billing provider plugin to [android-store](https://github.com/soomla/android-store).*


## android-store-amazon

android-store-amazon is a billing service plugin for android-store.

**December 3rd, 2014**:
Moving forward to Amazon IAP v2.0! See [changelog](https://github.com/soomla/android-store-amazon/blob/master/changelog.md)

## Getting Started

In order to work with this plugin you first need to go over android-store's [Getting Started](https://github.com/soomla/android-store#getting-started).

The steps to integrate this billing service are also in android-store's [Selecting Billing Service](https://github.com/soomla/android-store#amazon) but we will also write them here for convenience:


1. Add `in-app-purchasing-2.0.1.jar` (from `libs` folder) and `AndroidStoreAmazon.jar` (from `build` folder) to your project.

2. Make the following changes in AndroidManifest.xml:

Add amazon's ResponseReceiver to your `application` element. Also, you need to tell us what plugin you're using so add a meta-data tag for that:

  ```xml
  <receiver android:name = "com.amazon.device.iap.ResponseReceiver" >
    <intent-filter>
      <action android:name = "com.amazon.inapp.purchasing.NOTIFY"
              android:permission = "com.amazon.inapp.purchasing.Permission.NOTIFY" />
    </intent-filter>
  </receiver>
  <meta-data android:name="billing.service" android:value="amazon.AmazonIabService" />
  ```

Contribution
---
SOOMLA appreciates code contributions! You are more than welcome to extend the capabilities of SOOMLA.

Fork -> Clone -> Implement -> Add documentation -> Test -> Pull-Request.

IMPORTANT: If you would like to contribute, please follow our [Documentation Guidelines](https://github.com/soomla/android-store/blob/master/documentation.md). Clear, consistent comments will make our code easy to understand.

## SOOMLA, Elsewhere ...

+ [Framework Website](http://www.soom.la/)
+ [Knowledge Base](http://know.soom.la/)


<a href="https://www.facebook.com/pages/The-SOOMLA-Project/389643294427376"><img src="http://know.soom.la/img/tutorial_img/social/Facebook.png"></a><a href="https://twitter.com/Soomla"><img src="http://know.soom.la/img/tutorial_img/social/Twitter.png"></a><a href="https://plus.google.com/+SoomLa/posts"><img src="http://know.soom.la/img/tutorial_img/social/GoogleP.png"></a><a href ="https://www.youtube.com/channel/UCR1-D9GdSRRLD0fiEDkpeyg"><img src="http://know.soom.la/img/tutorial_img/social/Youtube.png"></a>

## License

Apache License. Copyright (c) 2012-2014 SOOMLA. http://soom.la
+ http://opensource.org/licenses/Apache-2.0
