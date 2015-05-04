from ctypes import *
import numpy as np
import time

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
print "testing frame rate..."

frame_count = 0
fps = 0
time_curr = time.time() * 1000
time_last = time_curr
test_time = 30
i = 0

while i < test_time:
	liblepton.lepton_get_frame(np.ctypeslib.as_ctypes(img_data), byref(frame_counter), byref(fpa_temp), byref(housing_temp))
	frame_count += 1
	time_curr = time.time() * 1000
	if time_curr - time_last >= 1000:
		time_last = time_curr
		fps = frame_count
		frame_count = 0
		print "fps: " + str(fps)
		i += 1

print ""
print "done."
print "disconnecting from lepton...",

liblepton.lepton_disconnect()

print "done."
print "exiting."