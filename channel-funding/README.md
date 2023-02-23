# Open Channel Interceptor plugin

This plugin provides an example of how to accept or reject `OpenChannel` messages received from remote peers based on a custom configuration file `channel_funding.conf` with parameters `open-channel-interceptor.min-active-channels` and `open-channel-interceptor.min-total-capacity`. It rejects `OpenChannel` requests from remote peers if they have less than the configured minimum public active channels or public total capacity.

Disclaimer: this plugin is for demonstration purposes only.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Build

To build this plugin, run the following command in this directory:

```sh
mvn package
```

## Run

To run eclair with this plugin, start eclair with the following command:

```sh
eclair-node-<version>/bin/eclair-node.sh <path-to-plugin-jar>/channel-funding-plugin-<version>.jar
```

## Commands

There are no cli commands for this plugin.

