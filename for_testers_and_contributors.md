### Welcome testers to nOREstone

NOREstone is an implementation of Sloimay's redstone simulator, named "Nodestone", for the ORE Minecraft server.


### Context:

**Nodestone**: *Nodestone* is a redstone simulator written in kotlin that can support multiple redstone 
simulating algorithms (that we call "backends") through a unified interface.\
If you want *Nodestone* to simulate my redstone, you have to programmatically get a grid of 3D blocks that's basically
the circuit you want to simulate. *Nodestone* will understand that 3D grid of blocks, and depending on the backends,
will simulate the redstone differently. (For example, some backends may simulate redstone fast, but with less vanilla
parity, while others simulates redstone slower with better parity.)

**nOREstone**: *Nodestone* (not n**ORE**stone) is standalone, it doesn't know about Minecraft (the 3D grid of blocks you 
send to it as the redstone circuit is actually universal, and does not know about Minecraft but I'll spare the details).\
If you want to run simulations on a Minecraft server, just make a plugin that gets the blocks of a redstone circuit, transform it 
into that universal 3D block grid representation, and send it over to *Nodestone*. To use *Nodestone*, you basically need a
translator that bridges the gap between actual Minecraft, and *Nodestone*. And that's exactly what
n**ORE**stone is.\
Interesting info for the nerds: n**ORE**stone isn't special, you could totally make a mod that bridges the gap between MC and *Nodestone*
with ease, or use *Nodestone* completely externally in a separate application, as long as a translator program is running that
bridges the gap.
 
**Note**: n**ORE**stone and *Nodestone* are two different projects, n**ORE**stone is just a plugin that
implements *Nodestone*. People working on both projects are of a very similar group, but *Nodestone* wasn't made specifically for ORE. It
was only a passion project Sloimay started for funsies, and Capo wanted it be implemented on ORE. XD

### Testing
Each player can select using a netherite shovel (or any wand item they want using /sim selwand) WorldEdit style.
Once they have selected the redstone circuit they want to simulate, they can do /sim compile <backend>, which will compile
their redstone circuits using *Nodestone* and get their simulation going. Do note that your simulation isn't frozen once you
compile a simulation. Your simulation and your selection are two separate entities, it's just that when you compile a simulation,
the plugin checks where your selection is to make the simulation at.\
Where you can select is very limited, you can only select blocks that you have the permission to access (= plots you own
or are trusted on). And you cannot make a simulation that intersects with someone else's.\
**Useful commands**: 
- `/sim tps <tps>`: change the TPS of your simulation (it even supports non-integer TPS!).
- `/sim tps`: check at what speed your simulation is currently going
- `/sim desel`: if you need to deselect your selection (you'll needd it)
- `/sim compile <backendId> <compile flags>`: compile a simulation
- `/sim clear`: remove your simulation from the world. You cannot create 2 simulations at the same time.
- `/sim freeze`: freeze your simulation
- `/sim step <game ticks>`: step through your simulation
- `/sim backends`: get a list of currently available backends\

When testing here are things to watch out for:
- Item NBT isn't translated very well when compiling. Containers with items with complex NBT may not get compiled correctly.
- The only backend that's available right now is the `shrimple` backend. It is a backend Sloimay made very quickly, for the fun
of it, but is the most accurate backend we have now. It emulates most components MCHPRS can, but is running an idealised version
of redstone, with some repeater locking tweaks so that most cases work. ***PLEASE: Do not report redstone implementation bugs
as "nOREstone bugs". This is work for contributors of the Nodestone backends. nOREstone has nothing to do with those bugs as it's just a bridge.*** 
- We are aware of simulated redstone circuits not getting updated once you clear the simulation. The circuit stays in limbo and will
be fixed in the next versions of n**ORE**stone.
- The way a *Nodestone* simulation sends its data back to the server is by sending block changes. So if you break a repeater while the simulation is going,
you'll see it magically reappear back once it changes state.
- While how redstone is simulated is out of scope for n**ORE**stone, the way the redstone from *Nodestone* is rendered back to the world is
n**ORE**stone's job. Please do tell us if you find bugs related to that. Especially when it comes to possible block NBT deletion.
- Thanks for testing!

### Contributing
Sloimay will probably not be the one managing contributions (but we'll see!), so we'll let Capo or whatever contribution admin fill this section out.
