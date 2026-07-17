#######################################################
#                                                                                             #
# RCBts-10.zip:  A Taxispeed Gauge for FS20002 aircraft            #
#                                                                                             #
#                      By Rob Barendregt            23 febr. 2003             #
#                                                     Release 1.0                       #
#######################################################

1. Introduction
===============
I find that maintaining a proper, constant speed when taxiing on an airport
is one of the most difficult things in FS2K2, especially for heavy jets and
when making sharp turns on taxiways.

This gauge will help you solve that.

Features:
- The target taxispeed can be adjusted from 3 - 40 knots.
  Note: based on actual groundspeed, so will also work with wind enabled.
- The actual taxispeed is kept constant within 1-2 knots of the set target speed.
- Indicator light, that shows whether the gauge is on or off.
- Click sounds.
- Auto-shutoff when the Parking Brakes are set.


2. Taxispeed gauge indicators
=============================
The gauge consists of 3 areas (see also their Tooltips):
- A button, to activate/deactivate the gauge.
  This button has an indicator light, colored:
  - RED: Gauge cannot be activated.
    The gauge can only be active when:
    - The Parking Brakes are released.
    - The aircraft is on the ground.
    - The sim is not in Slew mode.
  - OFF: Click button to activate the gauge.
  - GREEN: The gauge is operative.
- The Target Taxispeed (3 - 40 knots, the left two digits). Default: 10 knots.
  Click to increment or decrement the value.
  You can change the target speed at any time, even if the gauge is operative.
- The Actual Groundspeed (the right two digits)


3. How the gauge works ....
===========================
The gauge operates by setting the throttles to a specific value, according
to the following algoritme:
- If ActualSpeed is smaller then 1 knots:
  Throttles are set to 70%
- If ActualSpeed is between 1 knots and (TargetSpeed-2):
  Throttles are set to 40%
- If ActualSpeed is between (TargetSpeed-2) and TargetSpeed:
  - If ActualSpeed is increasing: Throttles are set to 20%
  - If ActualSpeed is decreasing: Throttles are set to 40%
- If ActualSpeed is between TargetSpeed and (TargetSpeed+2):
  - If ActualSpeed is increasing: Throttles are set to 0%
  - If ActualSpeed is decreasing: Throttles are set to 20%
- If ActualSpeed is greater then (TargetSpeed+2):
  Throttles are set to 0%

Obviously such an algoritme is a compromise, influenced by the following factors:
- The weight of the aircraft
- Spoolup/spooldown times of the engine(s).
- Smooth throttle behaviour vs. accurate speed control.

But with this algoritme it should work for any aircraft.


5. Frequently Asked Questions (FAQ)
===================================
Q1: Can I use this gauge without having the Pushback gauge installed ?
A1: Yes, you can.
    With one exception: for aircraft of which Engine-3 has Magneto's.
    Plus: the click-sounds won't work.
    You can add the gauge to any panel (either on the main panel window, or as overlay),
    but you have to figure out yourself how to place the gauge; the Readme
    of the Pushback gauge gives a lot of info on how-to do this.
    PLEASE DON'T AS ME FOR SUPPORT IF YOU WANT TO USE IT THIS WAY !!

Q2: When the gauge is active, the Thottle levers on my panel are very 'jumpy' even
    when the throttle levers should be steady (ActualSpeed < Targetspeed-2 or
    > Targetspeed+2).
A2: This means that the throttle axis of your yoke/joystick 'jitters' a bit, which
    makes FS2K2 continously responding to changes of your throttle axis.
    Remedy: set the throttles on your controller to 'idle'.
    If this doesn't work (then you will also have problems with Reverse Thrusters !):
    Create a small deadzone, either by increasing the Nullzone for the Throttle axis 
    in Options-Controls-Sensitivities or use FSUIPC for this.
    I recommend using FSUIPC for controller calibration anyway, since this is IMHO 
    the ONLY way to accurately calibrate all your controller axis in FS2K2. 
    You can download FSUIPC (made by Pete Dowson) a.o. from flightsim.com and avsim.com

Q3: Can I use this gauge to adjust the taxispeed of my AI aircraft ?
A3: No, just for the aircraft YOU are flying.


6. Copyrights and Disclaimer
============================
This gauge is freeware, and is available for your personal use.
It may NOT be sold, re-distributed, or (re-)uploaded to another website
(in ANY shape or form) without my explicite, written permission.

If you want to bundle this gauge with your (freeware !!) panel, you 
may do so AFTER my written permission, provided you include this 
README file AS-IS, without modification.

And obviously, installing & using this Taxispeed gauge is at your own risk !!


7. History
==========
Rel. 1.0: Initial release



I hope you enjoy using this gauge.
And I'm always open to questions, or suggestions for improvement.
(no guarantee that I will make them though).
But PLEASE, before asking me questions, make sure that the answer can not
be found in this Readme file.

Rob Barendregt, The Netherlands
Email: rc.barendregt@planet.nl

               **************************************

