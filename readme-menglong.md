查看小智设备是否连接成功
localhost:xiaozhi-server chenmenglong$ ls /dev/cu.*
/dev/cu.BLTH			/dev/cu.Bluetooth-Incoming-Port	/dev/cu.EDIFIERX3Air		/dev/cu.usbmodem5B141011261



擦除固件
pip3 install esptool && sudo python3 -m esptool -p /dev/cu.usbmodem5B141011261 -b 115200 erase_flash


刷固件
get_idf && idf.py -p /dev/cu.usbmodem5B141011261 flash


sudo lsof -ti:8080 | xargs sudo kill -9
