#include <stdio.h>
#include "liblepton.h"

int main( int argc, char **argv )
{
	uint16_t frame_buffer[IMAGE_WIDTH * IMAGE_HEIGHT] = {0};
	float fpa_temp = 0, housing_temp = 0;
	long frame_counter = 0;

	printf("starting program...\n");
	lepton_connect();
	printf("done. getting a frame...");
	lepton_get_frame(frame_buffer, frame_counter, fpa_temp, housing_temp);
	lepton_disconnect();
	printf("done. quitting...\n");
}

