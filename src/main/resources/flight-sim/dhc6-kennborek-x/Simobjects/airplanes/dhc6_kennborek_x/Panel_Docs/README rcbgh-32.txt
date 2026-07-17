###############################################################################
#                                                                      #
# RCBgh-32.zip:  Groundhandling gauges for FS2002 / FS2004 aircraft    #
#                                                                      #
#                        By Rob Barendregt               12 nov . 2003  #
#                                                         Release 3.2  #
########################################################################


PLEASE, READ THIS MANUAL CAREFULLY BEFORE INSTALLING & USING THESE GAUGES.

TO PRINT:
Open this file in Notepad, and select charactertype: FixedSys, Normal, 9 dots


This archive contains a set of groundhandling gauges which you can add to the
panel of any FS2002 AND FS2004 aircraft, such as a Pushback gauge and a
Taxispeed gauge.

They replace a simular set of gauges I made for FS2002, which will be no longer
supported.

Especially for existing users of these gauges:
I suggest to read the whole document again, although most of it will sound
familiar. Particularly of interest are:
Appendix-2: Why & How to upgrade to these new versions.
Appendix-3: How is the "XML sound" problem solved in FS2004 ?
Appendix-4: How to use only a subset of the gauges in this archive


*******************************************************************************
* This gaugeset is dedicated to Bill Morad, the author of the original FS2002 * 
* XMLsound gauge. Without him, I would probably never have created the        *
* Pushback gauge. Unfortunately Bill died from heart problems on 23 dec. 2002 *
*******************************************************************************


0. Contents
===========
1.     Introduction
2.     Installation
3.     Gauge operation
3.1    AUDIO operation.
3.2    Pushback operation
3.2.1  Pushback timer/counters, buttons and phases
3.2.2  Pushback Operation and Ground-Cockpit conversation
3.3    Taxispeed operation
3.3.1  Taxispeed indicators and dials
3.3.2  How the Taxispeed control works
3.4    Taxispeed Arming switches
3.4.1  ARM-P: Activate Taxispeed control after ParkingBrakes release
3.4.2  ARM-L: Activate Taxispeed control after landing
3.5    Parking Brakes gauge
3.6    Brakes Pressure gauge
3.7    Brakes Sound gauge
4.     Copyrights and Disclaimer
5.     Credits
6.     History and known problems
7.     Closure

Appendix-1: Gauge positioning in aircraft panels
Appendix-2: Why & How to upgrade to these new versions.
Appendix-3: How is the "XML sound" problem solved in FS2004
Appendix-4: How to use only a subset of the gauges in this archive
Appendix-5: The "brakes" problem in FS2002/FS2004.



1. Introduction
===============
Do you recognise this ??
- I push Shift-P to start Pushback, press 1 or 2 for a Left c.q. Right
  turn, but the aircraft doesn't make the turn ....
- I want to see the Pushback from SpotPlane View mode ....
- I want to Pushback my aircraft from the gate onto the taxiway,
  but I don't know when to start the Left or Right Turn ....
- When my aircraft is taxiing, I can't control the speed properly ....
- Are my toebrakes working correctly, what's the applied brake pressure ? ...

These gauges will solve these problems  !!
And add some nice Pushback conversation sounds, and click/warning sounds too.

These gauges can be installed in any panel for any aircraft, as an overlay
window activated by an (included) window toggle icon in your main panel window.

What they offer:
- A Pushback gauge:
  - The Pushback sequence can be performed automatically, by setting the 
    time it should be pushed straight back, plus the desired turn angle.
  - It uses the standard FS2002/FS2004 Pushback mechanisme, so you can hear the 
    engine/environment sounds during Pushback.
  - During Pushback, you can change the views or even PAUSE the system
    without disturbing the Pushback. 
  - To support Pushback operation, the cockpit-ground conversation is made
    audible.
- A Taxispeed gauge:
  Controls the groundspeed of the aircraft while taxiing, by manipulating the
  throttles AND the brakes if needed.
- A AutoTaxiAfterParkingbrakes switch:
  This switch allows you to arm the Taxispeed controller.
  When you release the ParkingBrakes, the Taxispeed controller is automatically
  activated.
- A AutoTaxiAfterLanding switch:
  This switch allows you to arm the Taxispeed controller.
  After the aircraft has landed, and the speed has decreased to below 40 Knots
  the Taxispeed controller is automatically activated. If applicable, it also
  retracts flaps/spoiler and deactivates reverse-thrust and the autobrakes.
- A ParkingBrake switch:
  This switch shows the position of the Parking Brakes, and lets you
  (de-)activate them.
- BrakePressure gauge:
  This gauge displays the braking pressure you apply with the toebrakes.
- BrakeSound gauge.
  This gauge makes the sound of braking audible.


2. Installation
===============

NOTE: This instruction is for FS2004; for FS2002 just read folder name
      ....\FS2002\ instead of ....\Flight Simulator 9\

1. Extract:

- File rcb_sound.gau to folder ....\Flight Simulator 9\Gauges\
- File rcb_groundhandling.cab also to folder ....\Flight Simulator 9\Gauges\
  IMPORTANT: DO NOT UNZIP THIS .CAB FILE !!
- The 11 .wav files to folder ....\Flight Simulator 9\Sound\

2. Add the gauges to the panel of your aircraft(s).

Placing the gauges in your panel is done by adding some lines to the panel.cfg
file of your aircraft, located in the ...\Flight Simulator 9\Aircraft\"your
aircraft"\Panel\ folder. The simplest way of doing that is by using Notepad.
*** MAKE A BACKUP OF YOUR panel.cfg FIRST ***

- In the [Window Titles] section, add the line:

Window**=Groundhandling

where '**' is the next free number.
For the default FS2004 Boeing 747_400 ONLY !!: '**' is 06


- In the [Window00] section, add the line:

gauge**=rcb_groundhandling!Icon_Pushback, aaa,bbb,12,12

where '**' is the next free number, 'aaa'and 'bbb' are the coordinates for the
groundhandling Icon on your panel and 12,12 is the size in pixels.
See also Appendix-1.

For the default FS2004 Boeing 747_400 ONLY !!:
           gauge58=rcb_groundhandling!Icon_Pushback, 128,111,12,12

- After the last [Window..] section, add the lines (use copy/paste to avoid
  typing errors):

[Window**]
size_mm=196,100                 // The relative window size in pixels.
ident=10005                     // The ident used by Icon_Pushback.
visible=0                       // 0: hidden when aircraft is loaded.
window_size= 0.3,0.2            // Window screen size: 30 % Hor., 20 % Vert.
position=0                      // 0: opens in top-left of screen.
background_color=16,16,16
gauge00=rcb_groundhandling!XMLSoundSwitch,             0,  0, 50, 36
gauge01=rcb_groundhandling!AutoTaxiAfterLanding,      50,  0, 48, 36
gauge02=rcb_groundhandling!AutoTaxiAfterParkingbrake, 98,  0, 48, 36
gauge03=rcb_groundhandling!ParkingBrakeSwitch,       146,  0, 50, 36
gauge04=rcb_groundhandling!PushbackDisplay,            0, 36, 86, 64
gauge05=rcb_groundhandling!TaxispeedFS2004,           86, 36, 60, 64 //NOTE-1
//gauge05=rcb_groundhandling!TaxispeedFS2002,         86, 36, 60, 64 //NOTE-1
//gauge05=rcb_groundhandling!TaxispeedNoBrakes,       86, 36, 60, 64 //NOTE-1
gauge06=rcb_groundhandling!BrakePressure,            146, 36, 50, 64
gauge07=rcb_groundhandling!PushbackStates, 0,0
gauge08=rcb_groundhandling!XMLSoundServer,0,0
gauge09=rcb_groundhandling!BrakeSound, 0,0
gauge10=rcb_sound!sound,0,0,,,9998 9999 95  // 95:Default overall sound volume

where '**' is the same number you used in the [Window Titles]

You can open the Groundhandling window by clicking the icon (the DownArrow) in
your main panel, or via menu Views - Instrument Panel.

NOTES:
1. IMPORTANT--IMPORTANT--IMPORTANT--IMPORTANT--IMPORTANT--IMPORTANT--IMPORTANT
   Due to differences in the Brakes implementation of FS2002 and FS2004, you
   must select the appropriate version of gauge05:
   - Taxispeed2004: uses the proportional FS brake commands. Use this when:
      - you use FS2004, WITH toebrake pedals.
   - Taxispeed2002: uses the non-proportinal (old) brake commands.Use this when
      - you use FS2004 WITHOUT toebrake pedals.
      - you use FS2002.
   - TaxispeedNoBrakes: does not use brake control at all. Use this when:
      - for whatever reason, you do not want to have the Taxispeed gauge
        to apply brakes to control the taxispeed.
   As you can see above, by default TaxipeedFS2004 is used (two "//" in
   in front of a line means that a gauge is disabled).
   You can use another version by removing/inserting "//" in front of a line.
   !! MAKE SURE YOU HAVE ONLY 1 VERSION ACTIVE !!
   For a detailed explanation, see Appendix-5.
2. These gauges do NOT require the installation of FSsound or FSUIPC.
3. You can change the "window_size= " if you prefer a smaller window.
   E.g. 0.15,0.1 makes it half the size.
4. If you like the window to appear on another default location, change the
   "position= " to a value 1 to 8 (with 8 being the bottom-right corner).
5. If you find the overall pushback conversation sounds too loud,  change the
   value 95 in "gauge09= " to any value between 80 and 100. 



3. Gauge operation
==================

A few general remarks first:
- All gauges have Tooltips on their clickable area's, describing their function
  Make sure you have the Tooltips enabled in FS2004.
- The lighted pushbuttons of all gauges have three main states, colored as
  follows:
  - RED:   Function Blocked (when clicked, the ProcedureError sound is played)
  - Off:   Function Inactive, click to activate.
  - GREEN: Function Active, click to de-activate.
- To set dials, you can also use the mouse scrollwheel instead of clicking.



3.1 AUDIO operation.
====================

With the AUDIO button you switch the sounds for all other gauges On/Off.
- Off:   All sounds disabled
- GREEN: All sounds enabled
- RED:   Switch disabled during Audio change (1 - 2 seconds)

IMPORTANT NOTE:
The soundhandling gauges uses the ADF for internal communication.
This means that if you want to use the ADF for it's original (NDB) purpose,
you must set the AUDIO switch to Off. See Appendix-3 for details.

Activation/Deactivation of AUDIO saves resp. restores the normal ADF frequency.



3.2 Pushback operation
======================
3.2.1 Pushback timer/counters, buttons and phases
=================================================

The gauge consists of 6 clickable areas:
- A pushbutton, to activate Pushback.
  This button has an indicator light, giving the phase of pushback:
  - RED: Pushback cannot be activated; make sure your Parking Brakes are set, 
         and the aircraft is standing still.
  - OFF: Click button to activate Pushback procedure.
  - Flashing GREEN: Waiting for release of the Parking Brakes,
    which starts the Pushback.
  - GREEN: Pushing back.
  - Flashing YELLOW: Pushback finished, set your Parking Brakes.
  - YELLOW: Pushback procedure terminating.
- A StraightBack Timer.
  This countdown Timer (0 - 99 seconds , the middle two digits) is started when
  you release the Parking Brakes and determines how long the aircraft is pushed
  straight back before the turn sequence starts.
  Click to increment or decrement the value.
- A LeftTurn Counter.
  This counter (0 - 90 degrees, the left two digits) determines the angle of
  the LeftTurn that you want the aircraft to make.
  Click to increment or decrement the value.
  So, after Pushback, the new heading of the aircraft equals the original
  heading PLUS the value you dialled into this LeftTurn Counter.
- A RightTurn Counter. 
  Same as the LeftTurn Timer, but in opposite direction.
- A LeftTurn arrow.
  Sets the LeftTurn Counter directly to 90 degrees (saves 90 mouse clicks :-),
  since this is probably the most used turn angle.
  After this, you can adjust the angle again by decrementing the LeftTurn
  Counter.
- A RightTurn arrow.
  See LeftTurn arrow.

A few notes: 
1. "Turning Left" means: the aircraft turns clockwise in TopView c.q. the
   heading increases while turning. As the standard FS pushback does.
2. When Pushback is active (GREEN light), you cannot change the Timer/Counter
   values anymore.
3. Obviously, the LeftTurn and RightTurn Counters are mutually exclusive.
   If you change one, the other is set to 0.
4. When Pushback is active (GREEN light), you can terminate the Pushback
   at anytime by setting the Parking Brakes; NOT by clicking the Pushback gauge
5. After setting up the Timer/Counters, and activating the Pushback by clicking
   the button, you can change the view mode at any time.
   E.g.: Go to SpotPlane view, release the Parking Brakes and observe the full
   Pushback in this view. The Pushback conversation will even be audible in
   SpotPlane view mode. You can even PAUSE FS2004 during the Pushback session.
6. After the StraightBack Timer becomes 0 (so before it makes the actual turn),
   the aircraft goes straight back another full length.
   This "full length" period depends on the aircraft:
   - For a Cessna 172: 4 seconds.
   - For a Boeing 747-400: 19 seconds.
7. You can use Pushback either with Engines off, or with engines running.
   You can also start your engines during Pushback operation.
8. When the Pushback is running and the StraightBack Timer becomes 0, you can
   dial in another value in this timer. This causes the aircraft to be pushed
   straight back (for the specified time) when the turn is completed.
9. When a pushback is active, the throttles are commanded to Idle position.

So, by setting the correct values for StraightBack Timer and Left /
RightTurnCounter, you specify:
- When the aircraft starts to turn.
- How many degrees the aircraft turns, either Left or Right.
- How long the aircraft is pushed back after the turn.



3.2.2 Pushback Operation and Ground-Cockpit conversation
========================================================
- Set the Parking Brakes.
  The RED light goes off.
  NOTE: the default FS2004 key for Set/Release Parking Brakes is . (PERIOD)
- Set the Center Timer and/or the Left/RightTurn Counter.
- Activate the Pushback procedure by clicking the Pushback button.
  The GREEN light starts flashing.

Cockpit: "Ground from cockpit"
Ground:  "Go ahead"
Cockpit: "Ready for pushback"
Ground:  "OK. Steering pin inserted, release brakes"

- The gauge now waits untill you release the Parking Brakes; a ticker sound is
  played in the mean time, to indicate that the gauge awaits a Parking Brakes
  operation.

Cockpit: "Brakes are released"
Ground (if engine1 is running): "OK. Pushing back"
Ground (if engine1 NOT running: "OK. Pushing back. All engines cleared for
                                 startup"

The following Pushback sequence is performed:
- The light goes solid GREEN. 
- If the StraightBack Timer was set, it counts down to 0 while the aircraft is
  pushed straight back.
- If the LeftTurn or RightTurn Counter was set, the aircraft is pushed straight
  back ANOTHER FULL LENGTH, and then turns the angle dialed into the Counter.
  During the turn the Counter indicates the remaining turn angle, and therefore
  counts down to 0.
- After the turn is completed, the aircraft is pushed straight back again for
  the new time you dialed into the StraightBack Timer (default: 2 second).
While a Pushback is active, an aircraft "rolling" sound is played; this sound
is especially noticeable when the engines are off during the pushback.

Ground: "Set parking brakes"

- The YELLOW light start flashing.
  The gauge waits untill you set the Parking Brakes; the ticker sound is played
  again.

- The light goes solid YELLOW.

Cockpit: "Parking brakes are set. Prepare aircraft for taxi and give 
          handsignal on left side"
Ground:  "OK. Towing system removed. Wait for handsignal on left-hand side"

- Now the pushback is terminated, and the light goes off.

 

3.3 Taxispeed operation.
========================
3.3.1 Taxispeed indicators and dials
====================================
The gauge consists of 3 clickable areas:
- A pushbutton, to activate/deactivate the gauge.
  This button has an indicator light, colored as follows:
  - RED: Gauge cannot be activated.
    The gauge can only be activated when:
    - the Parking Brakes are released.
    - the aircraft is on the ground.
    - the engines are running.
    - pushback is inactive.
  - OFF: Click button to activate the gauge.
  - GREEN: The Taxispeed control is Active.
- The Target Taxispeed (the Left two digits). Default: 15 knots.
  Click to increment or decrement the value.
  You can change the target speed at any time, even if the gauge is active.
- The Actual Groundspeed (the Right two digits).


3.3.2.How the Taxispeed control works
=====================================

When Active, the Taxispeed gauge controls the groundspeed of the aircraft while
taxiing, by manipulating the throttles according to an adaptive algorithm.
  - When activated, it disables the AP's AutoThrottle and SpeedHold functions. 
  - The target taxispeed can be adjusted from 3 - 40 knots.
  - The actual taxispeed is kept constant within 1 knot of the set target
    speed when the aircraft is taxiing straight-ahead; the speed may deviate 
    a bit more after initial acceleration, heavy braking or sharp turns.
  - The gauge automatically deactivates when:
    - The Parking Brakes are set.
    - The throttle are manually set to > 70% (usually to start takeoff).
    When this happens, an attention sound is played. 
  - When Brakes are applied by the user, the gauge temporarily sets the 
    throttles to idle, untill they are released again.
  - When the actual groundspeed - targetspeed is more than 4 knots, the Brakes
    are applied automatically. This happens in the following cases:
    - If you activate the gauge after a landing, when the groundspeed is still
      much larger than the set target speed
    - If you subtantially decrease the target speed while the gauge is already
      active.
    - Occasionally with heavy aircraft, after a long sharp turn (caused by the 
      weight of the plane, combined with the engine spooldown time).
    - When your aircraft accellerates even with throttles set to idle, e.g. 
      if there is a very strong tailwind while taxiing. Moreover, some aircraft
      in FS have the tendancy to start rolling even with throttles at idle.


A few notes:
1. The control is based on actual groundspeed, so it will also work with wind
   enabled.
2. The adaptive control algorithm is obviously a compromise between accuracy
   (overshoot, undershoot, reaction time), and is strongly influenced by
   aircraft characteristics like weight and spoolup/spooldown times. But you
   will find that it works rather smooth, without constant "jumping" throttles,
   and for most types of aircraft.
3. When the Taxispeed gauge is activated, make sure that your physical throttle
   axis is in the idle position, to avoid interferance with the control (due to
   axis "jitter"). If your throttle controller still jitters in idle (you will
   have problems with Reverse Thrust too !), create a small deadzone by
   increasing the Nullzone for the Throttle axis via menu
   Options-Controls-Sensitivities.
4. This Taxispeed gauge only work for the aircraft YOU are flying, NOT for your
   AI aircraft; that's another utility :-)




3.4 The Taxispeed ARM switches
==============================
3.4.1 ARM-P: Activate Taxispeed control after ParkingBrakes are released
========================================================================

This pushbutton switch allows you to Arm the Taxispeed controller, and is
colored as follows:
- RED:   Gauge cannot be activated.
         The gauge can only be activated when:
         - the Parking Brakes are released.
         - the aircraft is on the ground.
         - the engines are running.
         - pushback is inactive.
- Off:   Click to arm the Taxispeed controller.
- GREEN: The Taxispeed controller is armed. Click to disarm.

When active, and you release the Parking Brakes, the Taxispeed controller is
automatically activated, thus allowing you to:
  - Start taxiing without the panel being visible.
  - Automatically start taxiing after a Pushback session (by releasing the
    Parking Brakes). You can arm the Taxispeed control just before Pushback;
    obviously it then stays armed when you release the Parking Brakes to start
    the pushback.

If the Taxispeed controller is thus activated, an attention sound is played.



3.4.2 ARM-L: Activate Taxispeed control after landing
=====================================================

This pushbutton switch allows you to Arm the Taxispeed controller, and is
colored as follows:
- RED:   Gauge cannot be activated.
         The gauge can only be activated when the aircraft is in the air.
- Off:   Click to arm the Taxispeed controller.
- GREEN: The Taxispeed controller is armed. Click to disarm.

When active, the aircraft has landed AND the groundspeed has decreased to below
40 Knots:
  - The Taxispeed controller is automatically activated.
  - Spoilers and Flaps (if applicable to your aircraft) are retracted.
  - Autobrakes (if used) are set Off.
  - Reverse Thrusters (if used) are deactivated.



3.5 Parking Brakes gauge
========================

This pushbutton switch shows the position of the Parking Brakes, and is colored
as follows:
- Off:   Parking Brakes are released. Click to set.
- GREEN: Parking Brakes are set. Click to release.



3.6 Brake Pressure gauge
========================

This gauge displays the braking pressure (0 - 100 %) you apply with the
Left/Right toebrakes, indicated by two white bars. Note that when the Parking
Brakes are set, both bars always indicate max. pressure.



3.7 Brake Pressure gauge
========================

This gauge will trigger a brakes sound (if AUDIO is enabled) when the aircraft
is on the ground, the brake pressure is > 30% and groundspeed is > 3 knots.



4. Copyrights and Disclaimer
============================
This gaugeset is freeware, and is available for your personal use.

Without my explicite permission, it may NOT be sold, re-distributed and/or 
uploaded to another website or bulletin board (in ANY shape or form).

If you want to bundle (part of) this gaugeset with your (freeware !!) panel, 
you may ONLY do so AFTER my explicite permission and inclusion of this
README file AS-IS.

IMPORTANT!!: The included file rcb_sound.gau was specially written for my
groundhandling gauges, and may NOT BE REUSED by other XML gauges to prevent
potential sound conflicts on end-users flightsim PC's.

And obviously, installing & using this gauges is at your own risk !!
However, if you execute the Installation instructions properly, this gaugeset
will NOT crash you PC or FS, nor will it have substantial impact on performance


5. Credits
==========
- Arne Bartels, for his great introduction on how-to-make XML gauges.
- Ron Beal, for his re-recorded Pushback conversation sounds.
  I just modified them a bit to add the stereo effect.
- The FPDA group, for their original implementation of the pushback gauge. The
  pushback procedure I use is an almost exact copy.
- HGBG, for there original brakes sound; since their "old" BrakeSound gauge
  doesn't appear to work correctly in FS2004, I've made a new implementation.
- Trevor de Stigter, for his great tip on how to use the ADF for XML sound
  initiation.
- My beta-testers, who spent considerable time in trying to get these gauges
  and README bug-free (which I hope they are now). 
- And a very special **THANK YOU** to R.L. Clark (known for a.o his RadioCD
  player and soundswitch gauges). Based on Trevor's idea, and the extensive
  requirements I had for the new Pushback gauge, he was so very kind to
  implement the "C" part (file rcb_sound.gau) of the included XML sound
  initiation implementation.


6. History and known problems
=============================

- V3.2: New version of the Taxispeed2004 gauge.
  This solves the problem in V3.1 where the brakes are not released if you 
  use the combination FS2004, FSUIPC and toebrake pedals
- New advise when to use which version of Taxispeed. See appendix-5
- Sound problem:
  I know a few user PCs, where the pushback sounds sometimes are not played
  correctly (they don't start at all, or too late). The only common factor
  seems to be that they are using the sound device on their motherboard. 
  But, not all onboard sound devices give problems !
  I know of no cases where this problem occurs with seperate soundcards.
  Unfortunately, there's nothing I can do to solve this. 



7. Closure
==========

I hope you enjoy using these gauges; I know many people did with the previous
FS2002 versions. And I'm always open to questions, or suggestions for
improvement (no guarantee that I will make them though).
But PLEASE PLEASE, before asking me questions or report "bugs", make sure that
the answer can't be found in this README file; I have spent considerable time
in making this README, just to avoid wasting both YOUR time and MINE with
trivial questions and "issues" :-)

Rob Barendregt, The Netherlands
Email: rc.barendregt@planet.nl

               **************************************




Appendix-1 Gauge positioning in aircraft panels
===============================================
The Main panel usually consists of a bitmap (a .bmp file) on which the gauges
are positioned through coordinates.
Like: gauge**=rcb_groundhandling!Icon_Pushback, aaa,bbb,12,12 ,
 where 'aaa' is the horizontal, and 'bbb' the vertical positon on the panel.
The bitmap is a file xxxxx.bmp in the same folder as panel.cfg, and is
referenced in the panel.cfg by something like:

[Window00]
File=xxxxx.bmp

The coordinates 'aaa','bbb' are relative to this panel bitmap, and specify the
top-left corner of the gauge. Coordinates 0,0 indicate the top-left position of
the panel.

Most main panel bitmaps cover the entire screen, but some (like the default
747) only cover the lower part of the screen. Moreover, the coordinates (and
gauge size) also depend on the bitmap resolution.
So, in a 1024*768 pixel bitmap, coordinates 1024,768 specify the bottom-right
corner of the panel.
With this info, look at the coordinates of existing gauges in the [Window00]
section of the panel.cfg file. E.g. another Icon, and check whether their
coordinates corresponds with the postition you would expect on the panel.

To get the correct coordinates for the Groundhandling panel window Icon:
- Observe the main panel in FS2K2, and find a free spot.
- Look for a gauge nearby (e.g. another Icon), find this gauge in the panel.cfg
  file, and use nearby coordinates for the Groundhandling Icon.
Remember:
- 'aaa','bbb' specifies the top-left corner of the Icon.
- Increasing 'aaa' will shift the Icon to the right.
- Increasing 'bbb' will shift the Icon downwards.
- 12,12 (in my example) defines the size of the Icon in pixels, so using 24,24
  makes it twice as large. 
  12,12 is the normal Icon size for panels with a bitmap 640*480 resolution;
  and 16,16 for panels with a 1024*768 pixels.
- Make sure the Icon does not overlap with an existing gauge.

Note: after you edit and saved the panel.cfg file, in FS2002 you must select an
aircraft will another panel and then back again, to ensure that the new
panel.cfg is loaded ! 
In FS2004, just reselect the same aircraft again.


Appendix-2: Why & How to upgrade to these new versions.
=======================================================

For simplicity, I decided to bundle all my new groundhandling gauges into one
packaged .cab file, so you can safely install this package as described in 
section 2, while the old versions are still available if needed. To use the new
gauges, all you need to do is change the definition in the panel.cfg.
IMPORTANT: thoroughly follow the Installation instructions, because the gauge
definitions (including the panel window icon) have changed !!

So why should you upgrade (even if you still use FS2002) ?
First and foremost: the Pushback conversion sounds are now working in FS2004.
Other enhancements:
- For Pushback:
  - A few sounds are added, such as a tickersound when ParkingBrake action is
    required and an aircraft "roll" sound when the aircraft is being pushed
    back.
  - The conversation sounds are now in "stereo" (= Left/Right effect)
  - The conversation sounds are now properly timed; they are stopped c.q. not
    played when not needed anymore.
  - Because of the new sound mechanism, the Pushback with sounds are now
    possible for aircraft that have Magnetoes. If fact, it now works for ALL
    aircraft.
  - For the same reason, the gauge can now be added to the main (or other)
    panel window directly, without the annoying "all sounds playing
    simultaneously" problem.
  - The sounds can be switched Off/On at your discretion via the AUDIO button.
    (In fact this is not a feature, but a necessity to allow disjunct
    operation of the ADF and sound handling; see Appendix-3.
- For Taxispeed:
  - A new, adaptive algorithm to control the taxispeed; just as accurate as the 
    previous one, but now working for all aircraft types and without the
    "jumping throttles" effect.
  - When the actual groundspeed - targetspeed is more than 4 knots, the Brakes
    are applied.
- For the Taxispeed "Arm" switches:
  - A warning sound when the Taxispeed gauge goes from "Armed" to "Active"
- A Brakes Sound gauge.



Appendix-3: How is the "XML sound" problem solved in FS2004 ?
=============================================================

As you may know (and a lot of you do, given the huge amount of Emails I 
had :-) ), the old XMLsound mechanism doesn't work in FS2004. This is caused 
by a "feature" of FS2004, where FS variables not used in a specific aircraft
type (like the Magneto variables for Jet aircraft), are disabled. And this was
the basis for Bill Morad's XMLsound gauge.

The new sound mechanism is based on the (temporary) usage of the ADF; see the
description of the AUDIO button. This seemed like a good solution, especially
for "groundhandling" where the ADF has no purpose. 
Besides, most people don't use the ADF anyway.

The great idea of (mis-)using the ADF for this purpose was (as far as I'm
aware) from Trevor de Stigter, who also suggested to use the independant bits
in the decoded ADF frequency, thus allowing for 11 independantly playable
sounds. Based on this idea, R.L. Clark  was so kind to implement the "C"
part of this solution, based on the requirements I had for usage with my
Pushback gauge.
Like:
- Compatibility with FS2002.
- Independantly playable & stopable, timed sounds, both single-play and
  repeat-play.
- Preferably not be depending on other addon's, like FSUIPC or FSSOUND..
- Allowing normal use of the ADF when Pushback Audio is not activated.

This solution uses two "in-band" ADF frequencies to switch the Audio On and Off
(thus allowing to use the ADF as usual while Audio is Off). These frequencies,
by default, are resp. 999.8 and 999.9 Khz.
This "in-band" signalling has the obvious disadvantage that, when Audio is Off
and you set the Audio-On frequency manually, all of a sudden sounds may be
heard. So make sure that frequency 999.8 Khz is never used.
To my knowledge there are no NDB's in FS using this frequency.
And if AudioOn is set: don't change the ADF frequency :-)



Appendix-4: How to use only a subset of the gauges in this archive
==================================================================

Although I can't imagine why :-), some users only want to use a specific gauge
from this set, like just the Pushback (with or without conversation sounds) or
Taxispeed control.
Or place individual gauges in different panel windows.
With this new gaugeset there are no restrictions in terms of in which panel
window you place them, nor if this panel window is auto-visible at startup.

If you want to do this, keep the following guidelines in mind:
- Select the required gauges by adapting the panel.cfg definitions.
- If you want any of the Pushback or Click/Attention sounds audible, you need
  to include:
  - rcb_sound!sound                   (has no panel GUI)
  - rcb_groundhandling!XMLSoundServer (has no panel GUI)
  - rcb_groundhandling!XMLsoundSwitch This AudioOn/Off button gauge is optional
                                      If omitted, Audio is set On by default,
                                      so the ADF cannot be used normally.
- If you want the Pushback gauge:
  - rcb_groundhandling!PushbackStates (has no panel GUI)
  - rcb_groundhandling!PushbackDisplay
- If you want the Taxispeed gauge:
  - rcb_groundhandling!Taxispeed****
- If you want the Taxispeed ARM switch gauges:
  - rcb_groundhandling!AutoTaxiAfterParkingBrake
  - rcb_groundhandling!AutoTaxiAfterLanding
- If you want the BrakePressure gauge:
  - rcb_groundhandling!BrakePressure
- If you want the BrakeSound gauge:
  - rcb_groundhandling!BrakeSound

The following soundfiles are being used in this package:
- rcb_soundxml01_O: "Ground from cockpit ....."
- rcb_soundxml02_O: "Brakes are released. OK pushing back"
- rcb_soundxml03_O: "Set Parking Brakes"
- rcb_soundxml04_O: "Parking Brakes are set, prepare aircraft ...."
- rcb_soundxml05_O: "Brakes are released. OK pushing back. All engines ...."
- rcb_soundxml06_O: Click sound, when a pushbutton switch is clicked.
- rcb_soundxml07_O: Procedure error sound, when clicking Red-lighted button.
- rcb_soundxml08_L: Groundroll sound during Pushback.
- rcb_soundxml09_O: Attention sound, when the Taxispeed controller goes from 
                    Armed to Active, or when it is deactivated automatically.
- rcb_soundxml10_L: Attention sound, when Pushback awaits ParkingBrakes action.
- rcb_soundxml11_L: Brakes sound.



Appendix-5: The "brakes" problem in FS2002/FS2004
=================================================

In general, FS has two brake systems: ParkingBrakes and normal brakes.
The parking brakes works OK.

For its normal brakes, FS uses a diffential, proportional braking system: 
left and right brakes, pressure dependant.
There are three ways to operate the normal brakes:
- Via the keyboard (or joystick buttons):
  Commands BRAKES (works on left AND right brakes), BRAKES_LEFT, BRAKES_RIGHT.
  As in the default keys: "." "F11" "F12" ; I call them non-proportional.
  When giving these commands repetitively, 100% brake pressure is applied.
- Via toebrake pedals, seperate for Left/Right brakes and proportional with 
  the pedal pressure.
- Controlled from a gauge, either via proportional or non-proportional brake
  commands.

In FS2004 it is possible to define in the aircraft.cfg how the aircraft reacts
to a certain toebrakes pressure, and whether it has Parking brakes or not. Via:
[brakes]
toe_brakes_scale=1.4  //1.4: scalar; 0: brakes don't have any effect.
parking_brake=1       //1: parking brakes available.

FS2002 problems
---------------
- Using toebrake pedals:
  When a certain pedal pressure is applied, and the pressure on the pedals is
  reduced, FS2002 keeps the pressure as-is untill the pedal pressure is fully
  released.
- Using commands for differential, proportional brakes from a gauge:
  Once a certain pressure is commanded, FS will not release the brakes
  anymore untill the toebrake pedals are touched; i.e. untill FS sees that
  the pedals are in released position.

FS2004 problems 
---------------
- If the Parking brakes are set, and any brake command is given, the Parking
  brakes are released. Not very realistic IMHO.
- If FS2004 detects that proportional brakes are used, either from toebrake 
  pedals or controlled from a gauge (like my Taxispeed2004), it disables the
  non-proportinal brake commands, meaning that ".", "F11" and "F12" no longer 
  work. This is not a problem if you have toebrake pedals, but if you don't,
  you're stuck :-)

Using FSUIPC with toebrake calibration function
-----------------------------------------------
When you have the registered version of FSUIPC installed, with toebrake 
calibration set in FSUIPC, the problems above are mostly not there; because
FSUIPC interacts with the sim engine directly, implementing it's own way
of emulating "proportional" brakes (the same way it allready did for FS2000).



You may not understand all details explained above; it's just important to
realise that I couldn't make an implementation of the Taxispeed gauge, using
one type of brakes control that works in all situations and configurations.

Hence there are three versions:

- Taxispeed2004: uses the proportional FS brake commands. Use this when:
    - you use FS2004, WITH toebrake pedals.

- Taxispeed2002: uses the non-proportinal (old) brake commands. Use this when:
    - you use FS2004 WITHOUT toebrake pedals.
    - you use FS2002.

- TaxispeedNoBrakes: does not use brake control at all. Use this when:
    - for whatever reason, you do not want to have the Taxispeed gauge
      to apply brakes to control the taxispeed.

Note that the above applies whether you use FSUIPC or not.

**************************** End Of Document **********************************
