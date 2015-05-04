#!/bin/sh

g++ -c -pipe -O2 -Wall -W -I. -I./leptonSDKEmb32PUB/ -fPIC liblepton.cpp gpio.cpp SPI.cpp lepton_i2c.cpp
g++ -shared -o liblepton.so liblepton.o gpio.o SPI.o lepton_i2c.o leptonSDKEmb32PUB/Debug/libLEPTON_SDK.a
g++ -L. -Wl,-rpath=. -Wall -o liblepton_test -llepton main.cpp
