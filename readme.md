
### Known bugs
- Due to quirks with NBTAPI, I couldn't figure out how to get containers with items that have complex NBT on them to be recognized by redstone simulations properly. Named items should be fine. But do remember that as soon as one item is illegal, the SS returned by the container is undefined behavior.



### Permissions:

Notes:
- All permissions are independent, to allow for maximal customizability. While it can be error-prone, going through each permission of this list one at a time should do the trick.
- 


#### Command permissions:
- `norestone.cmd.sim`: "/sim"
- `norestone.cmd.sim.select`: "/sim desel", "/sim pos1", "/sim 1", "/sim pos2", "/sim 2"
- `norestone.cmd.sim.freeze`: "/sim freeze"
- `norestone.cmd.sim.step`: "/sim step"
- `norestone.cmd.sim.compile`: "/sim compile", "/sim clear"
- `norestone.cmd.sim.compile.backendaccess.<backendId>`: "/sim compile backendId"
- `norestone.cmd.sim.selwand`: "/sim selwand"
- `norestone.cmd.sim.tps`: "/sim tps"

Disclaimer: command permissions are separate from the underlying mechanism they allow.
Example: granting access to "/sim compile" does not necessarily allow you to compile, it only
allows you to run the command.


#### General permissions:
- `norestone.simulation.selection.select`: Base ability to select (=> in plots that are trusted or you're the owner of)
- `norestone.simulation.selection.select.bypass`: Ability to select everywhere
- `norestone.simulation.selection.changeselwand`: Ability to change the player's selelection wand
- `norestone.simulation.compile`: Ability to compile/clear
- `norestone.simulation.step`: Ability to step
- `norestone.simulation.freeze`: Ability to freeze
- `norestone.simulation.changetps`: Ability to change the tps

#### Number permissions:
- `norestone.simulation.maxtps.<int>`:
- `norestone.simulation.maxtps.bypass`:
- `norestone.simulation.maxdims.x.<int>`:
- `norestone.simulation.maxdims.y.<int>`:
- `norestone.simulation.maxdims.z.<int>`:
- `norestone.simulation.maxdims.bypass`:
- `norestone.simulation.maxvolume.<long>`:
- `norestone.simulation.maxvolume.bypass`:
