#!/bin/sh

if [ ! -d libs ];
then
    mkdir libs
fi

if [ ! -f libs/in-app-purchasing-amazon.jar ];
then
    curl -o Apps-SDK.zip https://amznadsi-a.akamaihd.net/public/mobileSdkDistribution/Apps-SDK.zip
	mkdir Apps-SDK
	unzip Apps-SDK.zip -d Apps-SDK
	rm Apps-SDK.zip
	cp Apps-SDK/Android/InAppPurchasing/2.0/lib/*.jar libs/in-app-purchasing-amazon.jar
	rm -rf Apps-SDK
fi