from ctypes import *
import numpy as np
import time
import os
import sys
import subprocess
import base64

def DetectNetwork(interface='eth0'):
	# Read ifconfig.txt and determine
	# if network is connected

	try:
		# Get network details using ifconfig
		filename = '/home/pi/picam-recorder/ifconfig_' + interface + '.txt'
		f = open(filename, "w")
		subprocess.call(["/sbin/ifconfig", interface], stdout=f)      
		f.close()

		# Check network details to see if interface exists
		f = open(filename, 'r')
		line = f.readline() # skip 1st line
		line = f.readline() # read 2nd line
		f.close()

		if line.find('inet addr:')>0:
			return True
		else:
			return False
	except:
		return False

def SaveFrame(recFile, imgData, frameCounter, fpaTemp, housingTemp):
	recFile.write(str(time.time()) + "," + str(frameCounter) + "," + str(fpaTemp) + "," + str(housingTemp) + "\n")
	recFile.write(base64.b64encode(imgData.tostring()))
	recFile.write("\n")


if DetectNetwork('eth0') == True:
	sys.exit(0)

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

print "discard 5 frames to clear SPI pipe...",
for i in range(5):
	liblepton.lepton_get_frame(np.ctypeslib.as_ctypes(img_data), byref(frame_counter), byref(fpa_temp), byref(housing_temp))
	time.sleep(0.05)
print "done."

print "getting frames and recording to '/home/pi/pylepton/recording.dat'..."
last_frame_counter = frame_counter.value
KeepGoing = True
# open recording file for writing
rec_file = open('/home/pi/pylepton/recording.dat', 'w+')

while KeepGoing == True:
	for i in range(26):
		liblepton.lepton_get_frame(np.ctypeslib.as_ctypes(img_data), byref(frame_counter), byref(fpa_temp), byref(housing_temp))
		if frame_counter.value > last_frame_counter:
			# save frame
			SaveFrame(rec_file, img_data, frame_counter.value, fpa_temp.value, housing_temp.value)
		last_frame_counter = frame_counter.value
	if DetectNetwork('eth0') == True:
		KeepGoing = False

# close recording file
rec_file.close()

print ""
print "done."
print "disconnecting from lepton...",
liblepton.lepton_disconnect()
print "done."
print "exiting."