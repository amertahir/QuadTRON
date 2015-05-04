import colorsys
import Image
import numpy as np
import base64
import sys


def RenderFrame(imgCounter, imgData, width, height):
	val_max = float(np.amax(imgData))
	val_min = float(np.amin(imgData))
	diff = float(val_max - val_min)
	# create image
	image = Image.new("RGB", (width, height), "black")
	pixels = image.load()
	# fill pixels
	for i in range(width):
		for j in range(height):
			color = colorsys.hls_to_rgb(1.0 - ((float(imgData[j, i]) - val_min) / diff), 0.5, 1.0)
			pixels[width-i-1, height-j-1] = (int(color[0] * 255), int(color[1] * 255), int(color[2] * 255))
	# save 
	image.save("frame" + str(imgCounter) + ".bmp")


width = 80
height = 60
img_data = None
timestamp = 0.0
frame_counter = 0
fpa_temp = 0.0
housing_temp = 0.0
rec_file = None
ln = 0
image_counter = 0

try:
	rec_file = open('recording.dat', 'r')
except:
	sys.exit(0)

for line in rec_file:
	if len(line) < 2:
		continue
	ln += 1
	if ln % 2 == 0:
		# parse and render frame image
		image_counter += 1
		img_data = np.reshape(np.fromstring(base64.b64decode(line[:-1]), np.uint16), (height, width))
		RenderFrame(image_counter, img_data, width, height)


print "done."
rec_file.close()