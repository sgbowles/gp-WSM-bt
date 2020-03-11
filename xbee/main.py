import time
from xbee import relay

#MSG_ACK = "OK"
device = "WSM"

INTERFACES = {
    relay.SERIAL: "Serial Port",
    relay.BLUETOOTH: "Bluetooth",
    relay.MICROPYTHON: "MicroPython"
}

# TODO: Only send messages when a "ON" message has been received. - Cameron
# TODO: Stop sending after an "OFF" message has been received. - Cameron
# TODO: Set a message interval in seconds. - Cameron
# TODO: Read WSM info and populate it into a string to send. - Cameron
# TODO: Error handle. - Stevan

def sending():
    while True:
        # relay.receive() -> Receives the message from the client
        relay_frame = relay.receive()

        # Stay in loop until an actual message is received
        while relay_frame is None:
            relay_frame = relay.receive()
            time.sleep(0.25)

        if relay_frame is not None:
        # Send acknowledgment message back to client
            relay.send(relay.BLUETOOTH, "OK")

            # Don't change this line. This is how it knows who to send the message to.
            sender = relay_frame["sender"]

            # Alter 'message' to send the desired payload
            file = open("example.txt", "r")
            lines = file.readlines()
            message = ""
            for line in lines:
                message = message + line
                message = message + "@@@"

            # Send the message and start reading relay frames again.
            relay.send(sender, message)

        else:
            # No connection between XBee and Android
            print("no connection")
            time.sleep(30)

def timeout():
    # After 5 times of trying or 2 minutes, XBee will break
    timeout = time.time() + 60 * 1  # 5 minutes from now
    while True:
        test = 0
        if test == 5 or time.time() > timeout:
            break
        test = (test + 1)
        
sending()
