import os
import sys

sys.path.append(os.path.sep.join([os.getcwd(), '']))

import numpy as np

import matplotlib.pyplot as plt
from src.core.query import Query

# set default fig size
plt.rcParams['figure.figsize'] = list(np.array([16.8, 10.14]) / 2)

q = Query({
    'partial_matches': True,
    'include_downstream': True,
    'indices': {
        'samples': [0],
        'model': [0],
        'sim': [0]
    }
}).run()

q.ap_time_and_location()