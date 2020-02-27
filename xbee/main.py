import time
from xbee import relay

MSG_ACK = "OK"

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

while True:
    # relay.receive() -> Receives the message from the client
    relay_frame = relay.receive()

    # Stay in loop until an actual message is received
    while relay_frame is None:
        relay_frame = relay.receive()
        time.sleep(0.25)

    # Send acknowledgment message back to client
    relay.send(relay.BLUETOOTH, MSG_ACK)

    # Don't change this line. This is how it knows who to send the message to.
    sender = relay_frame["sender"]

    # Alter 'message' to send the desired payload
    message = relay_frame["message"].decode("utf-8")

    # Send the message and start reading relay frames again.
    relay.send(sender, "%s" % message)
