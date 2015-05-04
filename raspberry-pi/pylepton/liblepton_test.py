from ctypes import *
import colorsys
import Image
import numpy as np
import sys

width = 80
height = 60
img_data = np.zeros((height, width), dtype=np.uint16)
frame_counter = c_long()
fpa_temp = c_float()
housing_temp = c_float()

print "loading library...",

liblepton = cdll.LoadLibrary("/home/pi/pylepton/liblepton/liblepton.so")

print "done."
print "connecting to lepton...",

liblepton.lepton_connect()

print "done."
print "getting a frame...",

liblepton.lepton_get_frame(np.ctypeslib.as_ctypes(img_data), byref(frame_counter), byref(fpa_temp), byref(housing_temp))

print "done."

print "Frame Counter: %d  FPA Temperature: %f  Housing Temperature: %f" % (frame_counter.value, fpa_temp.value, housing_temp.value)

print "generating image...",

val_max = float(np.amax(img_data))
val_min = float(np.amin(img_data))
diff = float(val_max - val_min)

image = Image.new("RGB", (width, height), "black")
pixels = image.load()

for i in range(width):
	for j in range(height):
		color = colorsys.hls_to_rgb(1.0 - ((float(img_data[j, i]) - val_min) / diff), 0.5, 1.0)
		pixels[i, j] = (int(color[0] * 255), int(color[1] * 255), int(color[2] * 255))

image.save("/home/pi/pylepton/frame.bmp")

print "done."
print "disconnecting from lepton...",

liblepton.lepton_disconnect()

print "done. exiting."