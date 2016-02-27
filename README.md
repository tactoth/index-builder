# index-builder
Sometimes going to different folders to find files can be a challenge, this project let you quickly build a index of all files inside a tree directory for easy discovery.

Example usage:

$ index-builder -pattern "(\w+)/1x_web/ic_(\w+)_black_24dp\.png" -result \$1/\$2 $(pwd) > generated_index.html

If you are frustrated by the Google material design project not providing an icon index file.
